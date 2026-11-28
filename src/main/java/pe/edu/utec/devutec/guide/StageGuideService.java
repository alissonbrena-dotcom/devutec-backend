package pe.edu.utec.devutec.guide;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.Skill;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStage;
import pe.edu.utec.devutec.model.contract.StageStatus;
import pe.edu.utec.devutec.pricing.FeatureQuantityDTO;
import pe.edu.utec.devutec.pricing.PriceEstimateDTO;
import pe.edu.utec.devutec.pricing.PriceEstimator;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;
import pe.edu.utec.devutec.support.AiGateway;
import pe.edu.utec.devutec.support.ChatTurnDTO;
import pe.edu.utec.devutec.support.StructuredReplies;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Sesión de acompañamiento del estudiante, como «Mini guía» de cada etapa:
// - Qué hacer: tareas concretas con horas (las horas totales salen de la tabla de horas, no de la IA)
// - Pregúntale al cliente, riesgos que se suelen olvidar y un consejo
// - Avance vs. horas («Diseño: 60 % · vas a tiempo») y revisión del precio por hora (calculados, sin IA)
// La IA arma la guía una sola vez por etapa; después solo se marcan tareas.
@Service
public class StageGuideService {

    static final int MIN_TASKS = 1;
    static final int MAX_TASKS = 7;
    static final int MAX_ITEMS = 4;
    // Si cobra menos que este porcentaje del promedio por hora, se le avisa (sin bloquearlo)
    static final int LOW_RATE_PERCENT = 60;
    // Margen para seguir «a tiempo» aunque vaya un poco por debajo de lo esperado
    static final int ON_TIME_MARGIN = 15;
    static final String SINGLE_STAGE_NAME = "Trabajo completo";

    private static final String RULES = """
            Eres Cuyi, el acompañante de DEVUTEC para estudiantes universitarios que hacen su primer proyecto \
            freelance (webs, automatizaciones y apps) para pequeños negocios. El estudiante sabe programar, \
            pero no sabe gestionar un proyecto. Arma su guía para UNA etapa del proyecto.

            Proyecto: «%s» (%s). Descripción: %s
            Funcionalidades: %s
            Cliente: %s. Estudiante: %s.
            Etapas del contrato: %s.
            Etapa de esta guía: «%s» (etapa %d de %d).%s%s

            Qué significa cada etapa:
            - Descubrimiento: entender el negocio, pedir contenido (fotos, textos, logo) y acordar el alcance.
            - Diseño: maqueta y estilo visual aprobados por el cliente ANTES de programar.
            - Desarrollo: programar lo acordado y probarlo, también en celular.
            - Entrega final: publicar (dominio, hosting), pruebas finales y explicarle al cliente cómo usarlo.
            - Trabajo completo: todo lo anterior en una sola etapa.

            Reglas:
            - Responde en español, tuteando, con frases cortas.
            - Solo tareas de ESTA etapa, concretas y que empiecen con un verbo. Nada de tareas de otras etapas.
            - Las preguntas son para el cliente, que NO sabe de tecnología: sin palabras técnicas y tuteándolo.
            - El consejo va dirigido al estudiante, en segunda persona, sin saludar y en una sola oración.
            - Riesgos: lo que un estudiante sin experiencia suele olvidar en esta etapa (contenido que debe \
            entregar el cliente, quién paga dominio y hosting, que hay máximo 2 rondas de cambios por etapa, \
            pruebas en celular, soporte después de la entrega). Solo los que apliquen.
            - No escribas precios ni montos.
            - Responde SOLO con un objeto JSON válido, sin texto antes ni después, con este formato:
            {"tasks": [{"title": "...", "hours": 2}], "clientQuestions": ["..."], "risks": ["..."], "tip": "..."}
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AiGateway aiGateway;
    private final ContractRepository contractRepository;
    private final StageGuideRepository guideRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PriceEstimator priceEstimator;
    private final Clock clock;

    @Autowired
    public StageGuideService(AiGateway aiGateway, ContractRepository contractRepository,
                             StageGuideRepository guideRepository, UserRepository userRepository,
                             CurrentUserService currentUserService, PriceEstimator priceEstimator) {
        this(aiGateway, contractRepository, guideRepository, userRepository, currentUserService, priceEstimator,
                Clock.systemDefaultZone());
    }

    StageGuideService(AiGateway aiGateway, ContractRepository contractRepository, StageGuideRepository guideRepository,
                      UserRepository userRepository, CurrentUserService currentUserService,
                      PriceEstimator priceEstimator, Clock clock) {
        this.aiGateway = aiGateway;
        this.contractRepository = contractRepository;
        this.guideRepository = guideRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.priceEstimator = priceEstimator;
        this.clock = clock;
    }

    @Transactional
    public StageGuideResponseDTO getOrCreate(Long contractId, Long stageId) {
        Contract contract = findContractOfStudent(contractId);
        ContractStage stage = findStage(contract, stageId);
        PriceEstimateDTO estimate = estimateFor(contract.getApplication().getProject());

        StageGuide guide = guideRepository.findByStage_Id(stageId).orElse(null);
        if (guide == null) {
            if (stage.getStatus() == StageStatus.CANCELLED) {
                throw new ApiException(HttpStatus.CONFLICT, "Esta etapa se canceló: ya no tiene guía");
            }
            guide = guideRepository.save(createGuide(contract, stage, estimate));
        }
        return toResponse(contract, stage, guide, estimate);
    }

    @Transactional
    public StageGuideResponseDTO updateTask(Long contractId, Long stageId, Long taskId, boolean done) {
        Contract contract = findContractOfStudent(contractId);
        ContractStage stage = findStage(contract, stageId);
        StageGuide guide = guideRepository.findByStage_Id(stageId)
                .orElseThrow(() -> new ResourceNotFoundException("Esta etapa todavía no tiene guía"));
        StageTask task = guide.getTasks().stream()
                .filter(item -> item.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada con id: " + taskId));
        task.setDone(done);
        task.setDoneAt(done ? LocalDateTime.now(clock) : null);
        return toResponse(contract, stage, guide, estimateFor(contract.getApplication().getProject()));
    }

    // ---------- Armar la guía con la IA ----------

    private StageGuide createGuide(Contract contract, ContractStage stage, PriceEstimateDTO estimate) {
        int[] reference = referenceHours(stage, contract.getStages().size(), estimate);
        StageGuideAiReply reply = StructuredReplies.ask(aiGateway, instructions(contract, stage, estimate, reference),
                List.of(new ChatTurnDTO("user", "Arma mi guía para la etapa «" + stage.getName() + "».")),
                StageGuideAiReply.class);

        List<StageGuideAiReply.AiTask> aiTasks = reply.tasks() == null ? List.of() : reply.tasks().stream()
                .filter(Objects::nonNull)
                .filter(task -> task.title() != null && !task.title().isBlank())
                .limit(MAX_TASKS)
                .toList();
        if (aiTasks.size() < MIN_TASKS) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Cuyi no pudo armar tu guía. Intenta de nuevo");
        }

        StageGuide guide = new StageGuide();
        guide.setStage(stage);
        guide.setClientQuestions(JSON.writeValueAsString(clean(reply.clientQuestions())));
        guide.setRisks(JSON.writeValueAsString(clean(reply.risks())));
        guide.setTip(shorten(reply.tip(), 300));
        if (reference != null) {
            guide.setReferenceHoursMin(reference[0]);
            guide.setReferenceHoursMax(reference[1]);
        }
        List<Integer> hours = scaleHours(aiTasks.stream().map(StageGuideAiReply.AiTask::hours).toList(), reference);
        for (int i = 0; i < aiTasks.size(); i++) {
            StageTask task = new StageTask();
            task.setPosition(i + 1);
            task.setTitle(shorten(aiTasks.get(i).title(), 200));
            task.setHours(hours.get(i));
            guide.addTask(task);
        }
        return guide;
    }

    private String instructions(Contract contract, ContractStage stage, PriceEstimateDTO estimate, int[] reference) {
        Project project = contract.getApplication().getProject();
        String clientName = userRepository.findById(project.getClientId()).map(User::getNombre).orElse("el cliente");
        String studentName = contract.getApplication().getFreelancer().getUser().getNombre();
        String features = estimate == null ? "no indicadas" : String.join(", ", estimate.features().stream()
                .map(feature -> feature.quantity() > 1 ? feature.name() + " × " + feature.quantity() : feature.name())
                .toList());
        String stages = String.join(", ", contract.getStages().stream().map(ContractStage::getName).toList());
        String hours = reference == null ? "" : "\nHoras de referencia de esta etapa: entre " + reference[0] + " y " + reference[1] + ".";
        String changes = stage.getStatus() == StageStatus.CHANGES_REQUESTED && stage.getChangeRequestNote() != null
                ? "\nEl cliente pidió estos cambios en esta etapa (incluye tareas para resolverlos): «" + stage.getChangeRequestNote() + "»."
                : "";
        return RULES.formatted(
                project.getTitle(),
                project.getCategory() == null ? "proyecto digital" : project.getCategory(),
                shorten(project.getDescription(), 600),
                features,
                clientName,
                studentName,
                stages,
                stage.getName(),
                stage.getPosition(),
                contract.getStages().size(),
                hours,
                changes);
    }

    // ---------- Horas ----------

    // Estimación del proyecto con la tabla de horas. null si el proyecto no tiene funcionalidades
    // (por ejemplo, proyectos creados antes del asistente): entonces no hay horas de referencia.
    private PriceEstimateDTO estimateFor(Project project) {
        if (project.getFeatures() == null || project.getFeatures().isEmpty()) return null;
        List<FeatureQuantityDTO> features = project.getFeatures().stream()
                .map(feature -> new FeatureQuantityDTO(feature.getCode(), feature.getQuantity()))
                .toList();
        List<Long> skillIds = project.getSkills().stream().map(Skill::getId).toList();
        return priceEstimator.estimate(skillIds, features);
    }

    // Horas de referencia de la etapa: las de la etapa con el mismo nombre, o las del proyecto completo
    // si el contrato tiene una sola etapa
    static int[] referenceHours(ContractStage stage, int stageCount, PriceEstimateDTO estimate) {
        if (estimate == null) return null;
        if (stageCount == 1 || SINGLE_STAGE_NAME.equalsIgnoreCase(stage.getName())) {
            return new int[]{estimate.hoursMin(), estimate.hoursMax()};
        }
        return estimate.stages().stream()
                .filter(item -> item.name().equalsIgnoreCase(stage.getName()))
                .findFirst()
                .map(item -> new int[]{Math.max(1, item.hoursMin()), Math.max(1, item.hoursMax())})
                .orElse(null);
    }

    // La IA reparte el trabajo entre tareas; el total se ajusta al punto medio de las horas de referencia.
    // Sin referencia, se usan las horas de la IA (entre 1 y 20 por tarea).
    static List<Integer> scaleHours(List<Integer> aiHours, int[] reference) {
        List<Integer> base = aiHours.stream().map(hours -> Math.max(1, Math.min(20, hours))).toList();
        if (reference == null) return base;
        int target = (int) Math.round((reference[0] + reference[1]) / 2.0);
        int sum = base.stream().mapToInt(Integer::intValue).sum();
        List<Integer> scaled = new ArrayList<>();
        for (int hours : base) {
            scaled.add(Math.max(1, (int) Math.round((double) hours * target / sum)));
        }
        return scaled;
    }

    // ---------- Respuesta ----------

    private StageGuideResponseDTO toResponse(Contract contract, ContractStage stage, StageGuide guide,
                                             PriceEstimateDTO estimate) {
        List<StageGuideResponseDTO.Task> tasks = guide.getTasks().stream()
                .map(task -> new StageGuideResponseDTO.Task(task.getId(), task.getTitle(), task.getHours(), task.isDone()))
                .toList();
        return new StageGuideResponseDTO(
                stage.getId(),
                stage.getName(),
                tasks,
                JSON.readValue(guide.getClientQuestions(), STRING_LIST),
                JSON.readValue(guide.getRisks(), STRING_LIST),
                guide.getTip(),
                progress(contract, stage, tasks, LocalDate.now(clock)),
                priceCheck(contract, estimate));
    }

    static StageGuideResponseDTO.Progress progress(Contract contract, ContractStage stage,
                                                    List<StageGuideResponseDTO.Task> tasks, LocalDate today) {
        int total = tasks.stream().mapToInt(StageGuideResponseDTO.Task::hours).sum();
        int done = tasks.stream().filter(StageGuideResponseDTO.Task::done).mapToInt(StageGuideResponseDTO.Task::hours).sum();
        int percent = total == 0 ? 0 : (int) Math.round(100.0 * done / total);
        boolean finished = stage.getStatus() == StageStatus.DELIVERED || stage.getStatus() == StageStatus.APPROVED;

        LocalDate due = stage.getDueDate();
        LocalDate start = startOf(contract, stage);
        if (due == null || start == null || finished) {
            String label = stage.getName() + ": " + percent + " %" + (finished ? " · entregada" : "");
            return new StageGuideResponseDTO.Progress(done, total, percent, null, "NO_DATE", due, label);
        }
        long days = Math.max(1, ChronoUnit.DAYS.between(start, due));
        long elapsed = Math.max(0, Math.min(days, ChronoUnit.DAYS.between(start, today)));
        int expected = (int) Math.round(100.0 * elapsed / days);
        boolean onTime = percent >= expected - ON_TIME_MARGIN;
        String label = stage.getName() + ": " + percent + " % · " + (onTime ? "vas a tiempo" : "vas un poco atrasado");
        return new StageGuideResponseDTO.Progress(done, total, percent, expected, onTime ? "ON_TIME" : "BEHIND", due, label);
    }

    // La etapa empieza cuando vence la anterior; la primera, cuando empezó el contrato
    private static LocalDate startOf(Contract contract, ContractStage stage) {
        return contract.getStages().stream()
                .filter(other -> other.getPosition() == stage.getPosition() - 1)
                .map(ContractStage::getDueDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(contract.getStartedAt() == null ? null : contract.getStartedAt().toLocalDate());
    }

    static StageGuideResponseDTO.PriceCheck priceCheck(Contract contract, PriceEstimateDTO estimate) {
        if (estimate == null || estimate.hoursMax() <= 0 || estimate.hourlyRate() == null
                || contract.getPayment() == null || contract.getPayment().getAmount() == null) {
            return null;
        }
        BigDecimal amount = contract.getPayment().getAmount();
        BigDecimal middleHours = BigDecimal.valueOf((estimate.hoursMin() + estimate.hoursMax()) / 2.0);
        BigDecimal hourly = amount.divide(middleHours, 0, RoundingMode.HALF_UP);
        BigDecimal average = estimate.hourlyRate().setScale(0, RoundingMode.HALF_UP);
        boolean low = hourly.multiply(BigDecimal.valueOf(100))
                .compareTo(average.multiply(BigDecimal.valueOf(LOW_RATE_PERCENT))) < 0;
        String hoursText = estimate.hoursMin() + " y " + estimate.hoursMax() + " horas";
        String message = low
                ? "Ojo: proyectos así toman entre " + hoursText + ". Con S/ " + amount.stripTrailingZeros().toPlainString()
                + " estás cobrando unos S/ " + hourly.toPlainString() + " la hora; el promedio en DEVUTEC es S/ "
                + average.toPlainString() + ". Tenlo en cuenta para tu próximo proyecto."
                : "Tu precio está bien: proyectos así toman entre " + hoursText + " y estás cobrando unos S/ "
                + hourly.toPlainString() + " la hora (el promedio en DEVUTEC es S/ " + average.toPlainString() + ").";
        return new StageGuideResponseDTO.PriceCheck(amount, estimate.hoursMin(), estimate.hoursMax(), hourly, average, low, message);
    }

    // ---------- Reglas y utilidades ----------

    private Contract findContractOfStudent(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado con id: " + contractId));
        User user = currentUserService.getCurrentUser();
        if (!user.getId().equals(contract.getApplication().getFreelancer().getUser().getId())) {
            throw new ForbiddenOperationException("Solo el estudiante del contrato puede ver esta guía");
        }
        return contract;
    }

    private static ContractStage findStage(Contract contract, Long stageId) {
        return contract.getStages().stream()
                .filter(stage -> stage.getId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Etapa no encontrada con id: " + stageId));
    }

    private static List<String> clean(List<String> items) {
        if (items == null) return List.of();
        return items.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .limit(MAX_ITEMS)
                .toList();
    }

    private static String shorten(String value, int max) {
        if (value == null || value.isBlank()) return max >= 600 ? "sin descripción" : null;
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
