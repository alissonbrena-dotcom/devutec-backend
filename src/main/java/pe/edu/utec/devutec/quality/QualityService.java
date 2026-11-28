package pe.edu.utec.devutec.quality;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStage;
import pe.edu.utec.devutec.model.contract.StageStatus;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.ContractStagePlan;
import pe.edu.utec.devutec.service.CurrentUserService;
import pe.edu.utec.devutec.support.AiGateway;
import pe.edu.utec.devutec.support.ChatTurnDTO;
import pe.edu.utec.devutec.support.StructuredReplies;
import pe.edu.utec.devutec.service.SocialLinks;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Estándar de calidad de DEVUTEC (punto 14 del plan): «solo entregamos proyectos que pasan nuestro estándar».
// - En proyectos web, la entrega final se bloquea hasta que la web publicada cumpla los mínimos de PageSpeed
//   (rendimiento, accesibilidad, buenas prácticas y SEO, en celular) y use HTTPS.
// - Si no cumple, la IA convierte lo que falla en tareas concretas para el estudiante, que arregla y vuelve a
//   revisar antes de la fecha de la etapa.
// - Si Google no responde, no se bloquea: una falla externa no puede trabar al estudiante.
@Slf4j
@Service
public class QualityService {

    static final String WEB = "WEB";
    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d 'de' MMMM", ES);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String RULES = """
            Eres el revisor de calidad de DEVUTEC, una plataforma donde estudiantes universitarios hacen webs \
            para pequeños negocios. La web del estudiante NO pasó el estándar de calidad de DEVUTEC y no puede \
            hacer la entrega final hasta arreglarlo.
            Proyecto: «%s». Estudiante: %s. Fecha límite de la etapa: %s.

            Lo que no cumple el estándar (esto bloquea la entrega):
            %s
            Puntos a mejorar que encontró PageSpeed de Google:
            %s
            Reglas:
            - Responde en español, tuteando al estudiante.
            - "tasks": tareas concretas y accionables para pasar el estándar, ordenadas por impacto (primero lo que \
            bloquea). Cada tarea empieza con un verbo y dice qué hacer, por ejemplo «Comprime las fotos de la carta \
            a WebP de menos de 200 KB». No inventes problemas que no estén en la lista.
            - "forClient": para el cliente, que NO sabe de tecnología: en 1 o 2 oraciones, que su web se está \
            revisando para que cargue rápido y se vea bien en celulares, y que el estudiante está haciendo ajustes. \
            Sin palabras técnicas y sin alarmar.
            - Responde SOLO con un objeto JSON válido, sin texto antes ni después. Formato:
            {"tasks": ["..."], "forClient": "..."}
            """;

    private final AiGateway aiGateway;
    private final ContractRepository contractRepository;
    private final QualityCheckRepository checkRepository;
    private final CurrentUserService currentUserService;
    private final PageSpeedClient pageSpeed;
    private final Minimums minimums;
    private final boolean enabled;
    private final int dailyLimit;
    private final Clock clock;
    // Revisiones hechas hoy por cada estudiante: "id:fecha" → cantidad
    private final Map<String, Integer> usage = new ConcurrentHashMap<>();

    // Puntajes mínimos de 0 a 100
    public record Minimums(int performance, int accessibility, int bestPractices, int seo) {
    }

    @Autowired
    public QualityService(AiGateway aiGateway, ContractRepository contractRepository,
                          QualityCheckRepository checkRepository, CurrentUserService currentUserService,
                          PageSpeedClient pageSpeed,
                          @Value("${quality.enabled:true}") boolean enabled,
                          @Value("${quality.min-performance:50}") int minPerformance,
                          @Value("${quality.min-accessibility:80}") int minAccessibility,
                          @Value("${quality.min-best-practices:80}") int minBestPractices,
                          @Value("${quality.min-seo:80}") int minSeo,
                          @Value("${quality.daily-limit:15}") int dailyLimit) {
        this(aiGateway, contractRepository, checkRepository, currentUserService, pageSpeed,
                new Minimums(minPerformance, minAccessibility, minBestPractices, minSeo), enabled, dailyLimit,
                Clock.systemDefaultZone());
    }

    QualityService(AiGateway aiGateway, ContractRepository contractRepository, QualityCheckRepository checkRepository,
                   CurrentUserService currentUserService, PageSpeedClient pageSpeed, Minimums minimums,
                   boolean enabled, int dailyLimit, Clock clock) {
        this.aiGateway = aiGateway;
        this.contractRepository = contractRepository;
        this.checkRepository = checkRepository;
        this.currentUserService = currentUserService;
        this.pageSpeed = pageSpeed;
        this.minimums = minimums;
        this.enabled = enabled;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    // ---------- Revisar la web (estudiante) ----------

    @Transactional
    public QualityCheckResponseDTO check(Long contractId, Long stageId, String rawUrl) {
        Contract contract = findContract(contractId);
        User user = currentUserService.getCurrentUser();
        if (!user.getId().equals(contract.getApplication().getFreelancer().getUser().getId())) {
            throw new ForbiddenOperationException("Solo el estudiante del contrato puede revisar su web");
        }
        ContractStage stage = findStage(contract, stageId);
        if (!isWeb(contract)) {
            throw new InvalidOperationException("La revisión de calidad es para proyectos web");
        }
        if (stage.getStatus() == StageStatus.APPROVED || stage.getStatus() == StageStatus.CANCELLED) {
            throw new InvalidOperationException("Esta etapa ya se cerró");
        }
        String url = SocialLinks.link(rawUrl, "El link no parece válido, por ejemplo https://mi-pizzeria.vercel.app");
        if (url == null) {
            throw new InvalidOperationException("Pega el link de tu web publicada");
        }

        useOne(user.getId());
        QualityCheck check = new QualityCheck();
        check.setStage(stage);
        check.setUrl(url);
        QualityCheckContent content;
        try {
            PageSpeedReport report = pageSpeed.analyze(url);
            check.setPerformance(report.performance());
            check.setAccessibility(report.accessibility());
            check.setBestPractices(report.bestPractices());
            check.setSeo(report.seo());
            check.setHttps(report.https());
            List<String> failures = failures(report, minimums);
            check.setStatus(failures.isEmpty() ? QualityStatus.PASSED : QualityStatus.FAILED);
            content = failures.isEmpty()
                    ? new QualityCheckContent(List.of(), report.issues(), List.of(), null)
                    : withTasks(contract, stage, failures, report.issues());
        } catch (PageSpeedClient.SiteNotReachableException e) {
            check.setStatus(QualityStatus.FAILED);
            content = new QualityCheckContent(List.of("No pudimos abrir tu web. Revisa que esté publicada, que el link "
                    + "sea público y que cargue en el celular."), List.of(), List.of(), null);
        } catch (PageSpeedClient.PageSpeedUnavailableException e) {
            // Falla de Google: no cuenta para el límite y no bloquea la entrega
            giveBackOne(user.getId());
            check.setStatus(QualityStatus.UNAVAILABLE);
            content = new QualityCheckContent(List.of(), List.of(), List.of(), null);
        }
        check.setContent(JSON.writeValueAsString(content));
        return toResponse(checkRepository.save(check), content);
    }

    // ---------- Ver la última revisión (cliente y estudiante) ----------

    @Transactional(readOnly = true)
    public QualityOverviewDTO overview(Long contractId, Long stageId) {
        Contract contract = findContract(contractId);
        ensureIsParticipant(contract);
        ContractStage stage = findStage(contract, stageId);
        QualityCheckResponseDTO last = checkRepository.findFirstByStage_IdOrderByCreatedAtDesc(stageId)
                .map(check -> toResponse(check, JSON.readValue(check.getContent(), QualityCheckContent.class)))
                .orElse(null);
        return new QualityOverviewDTO(isRequired(contract, stage), isWeb(contract), minimums, last);
    }

    // ---------- Bloqueo de la entrega final ----------

    // Se llama al entregar: en la entrega final de un proyecto web, la última revisión tiene que haber pasado
    @Transactional(readOnly = true)
    public void ensurePassed(Contract contract, ContractStage stage) {
        if (!isRequired(contract, stage)) return;
        QualityCheck last = checkRepository.findFirstByStage_IdOrderByCreatedAtDesc(stage.getId()).orElse(null);
        String deadline = stage.getDueDate() == null ? "" : " antes del " + stage.getDueDate().format(DAY);
        if (last == null) {
            throw new InvalidOperationException("Antes de la entrega final, revisa tu web con el estándar de calidad "
                    + "de DEVUTEC (botón «Revisar mi web»).");
        }
        if (last.getStatus() == QualityStatus.FAILED) {
            throw new InvalidOperationException("Tu web todavía no cumple el estándar de calidad de DEVUTEC. "
                    + "Arregla lo pendiente y revísala de nuevo" + deadline + ".");
        }
    }

    boolean isRequired(Contract contract, ContractStage stage) {
        return enabled && isWeb(contract) && ContractStagePlan.isLastStage(contract, stage);
    }

    private static boolean isWeb(Contract contract) {
        return WEB.equalsIgnoreCase(contract.getApplication().getProject().getCategory());
    }

    // ---------- El estándar ----------

    // Lo que no cumple, en palabras simples. Vacío = pasa.
    static List<String> failures(PageSpeedReport report, Minimums min) {
        List<String> failures = new ArrayList<>();
        below(failures, "Rendimiento", report.performance(), min.performance(), "tu web carga lento en celulares");
        below(failures, "Accesibilidad", report.accessibility(), min.accessibility(),
                "hay partes difíciles de leer o de usar para algunas personas");
        below(failures, "Buenas prácticas", report.bestPractices(), min.bestPractices(),
                "hay errores o prácticas inseguras en la página");
        below(failures, "SEO", report.seo(), min.seo(), "a Google le cuesta encontrar y mostrar tu web");
        if (!report.https()) {
            failures.add("Seguridad: tu web no usa HTTPS (el candado del navegador). Publícala con https://.");
        }
        return failures;
    }

    private static void below(List<String> failures, String label, int value, int minimum, String meaning) {
        if (value < minimum) {
            failures.add(label + ": " + value + " de 100 (mínimo " + minimum + "): " + meaning + ".");
        }
    }

    // ---------- IA: de lo que falla a tareas ----------

    private QualityCheckContent withTasks(Contract contract, ContractStage stage, List<String> failures,
                                          List<PageSpeedReport.Issue> issues) {
        try {
            QualityAiReply reply = StructuredReplies.ask(aiGateway, instructions(contract, stage, failures, issues),
                    List.of(new ChatTurnDTO("user", "Dame las tareas para que mi web pase el estándar.")),
                    QualityAiReply.class);
            List<String> tasks = clean(reply == null ? null : reply.tasks());
            String forClient = reply == null || reply.forClient() == null || reply.forClient().isBlank()
                    ? null : reply.forClient().trim();
            if (!tasks.isEmpty()) {
                return new QualityCheckContent(failures, issues, tasks, forClient);
            }
        } catch (RuntimeException e) {
            log.warn("La IA no armó las tareas de calidad: {}", e.getMessage());
        }
        // Sin IA igual se muestra qué arreglar: los puntos de Google como tareas
        return new QualityCheckContent(failures, issues, fallbackTasks(issues), null);
    }

    private String instructions(Contract contract, ContractStage stage, List<String> failures,
                                List<PageSpeedReport.Issue> issues) {
        Project project = contract.getApplication().getProject();
        String issueLines = issues.isEmpty() ? "- (sin detalle)" : issues.stream()
                .map(issue -> "- [" + issue.category() + "] " + issue.title()
                        + (issue.detail().isBlank() ? "" : " (" + issue.detail() + ")"))
                .collect(Collectors.joining("\n"));
        return RULES.formatted(
                project.getTitle(),
                contract.getApplication().getFreelancer().getUser().getNombre(),
                stage.getDueDate() == null ? "sin fecha" : stage.getDueDate().format(DAY),
                failures.stream().map(failure -> "- " + failure).collect(Collectors.joining("\n")),
                issueLines);
    }

    static List<String> fallbackTasks(List<PageSpeedReport.Issue> issues) {
        return issues.stream()
                .map(issue -> issue.title() + (issue.detail().isBlank() ? "" : " (" + issue.detail() + ")"))
                .limit(6)
                .toList();
    }

    private static List<String> clean(List<String> items) {
        if (items == null) return List.of();
        return items.stream().filter(item -> item != null && !item.isBlank()).map(String::trim).limit(6).toList();
    }

    // ---------- Utilidades ----------

    private QualityCheckResponseDTO toResponse(QualityCheck check, QualityCheckContent content) {
        List<QualityCheckResponseDTO.Score> scores = check.getPerformance() == null ? List.of() : List.of(
                score("performance", "Rendimiento", check.getPerformance(), minimums.performance()),
                score("accessibility", "Accesibilidad", check.getAccessibility(), minimums.accessibility()),
                score("best-practices", "Buenas prácticas", check.getBestPractices(), minimums.bestPractices()),
                score("seo", "SEO", check.getSeo(), minimums.seo()));
        return new QualityCheckResponseDTO(
                check.getId(),
                check.getStage().getId(),
                check.getUrl(),
                check.getStatus(),
                scores,
                check.getHttps(),
                content.failures(),
                content.issues(),
                content.tasks(),
                content.forClient(),
                check.getStage().getDueDate(),
                check.getCreatedAt());
    }

    private static QualityCheckResponseDTO.Score score(String key, String label, Integer value, int minimum) {
        return new QualityCheckResponseDTO.Score(key, label, value, minimum, value != null && value >= minimum);
    }

    private Contract findContract(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado con id: " + contractId));
    }

    private static ContractStage findStage(Contract contract, Long stageId) {
        return contract.getStages().stream()
                .filter(stage -> stage.getId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Etapa no encontrada con id: " + stageId));
    }

    private void ensureIsParticipant(Contract contract) {
        Long userId = currentUserService.getCurrentUser().getId();
        boolean isClient = userId.equals(contract.getApplication().getProject().getClientId());
        boolean isStudent = userId.equals(contract.getApplication().getFreelancer().getUser().getId());
        if (!isClient && !isStudent) {
            throw new ForbiddenOperationException("No tienes acceso a este contrato");
        }
    }

    private void useOne(Long userId) {
        String key = userId + ":" + LocalDate.now(clock);
        int used = usage.merge(key, 1, Integer::sum);
        if (used > dailyLimit) {
            usage.merge(key, -1, Integer::sum);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Llegaste al límite de revisiones de hoy. Vuelve mañana");
        }
    }

    private void giveBackOne(Long userId) {
        usage.merge(userId + ":" + LocalDate.now(clock), -1, Integer::sum);
    }
}
