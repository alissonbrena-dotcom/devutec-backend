package pe.edu.utec.devutec.assistant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.model.Skill;
import pe.edu.utec.devutec.pricing.BudgetBreakdownDTO;
import pe.edu.utec.devutec.pricing.CommissionCalculator;
import pe.edu.utec.devutec.pricing.FeatureQuantityDTO;
import pe.edu.utec.devutec.pricing.FuncionalidadReferencia;
import pe.edu.utec.devutec.pricing.FuncionalidadReferenciaRepository;
import pe.edu.utec.devutec.pricing.PaymentPlanner;
import pe.edu.utec.devutec.pricing.PriceEstimateDTO;
import pe.edu.utec.devutec.pricing.PriceEstimator;
import pe.edu.utec.devutec.repository.SkillRepository;
import pe.edu.utec.devutec.service.CurrentUserService;
import pe.edu.utec.devutec.support.AiGateway;
import pe.edu.utec.devutec.support.ChatTurnDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Asistente IA que arma el proyecto del cliente conversando, como un consultor.
// La IA solo habla del proyecto (si le preguntan otra cosa, outOfScope = true y se responde un mensaje fijo),
// devuelve campos fijos (salidas estructuradas) y nunca pone precios: los calcula PriceEstimator.
@Service
public class ProjectAssistantService {

    static final String OUT_OF_SCOPE_REPLY =
            "Solo puedo ayudarte a armar tu proyecto digital en DEVUTEC. ¿Me cuentas qué necesitas para tu negocio?";

    private static final String RULES = """
            Eres el asistente de proyectos de DEVUTEC, una plataforma peruana donde negocios pequeños contratan a \
            estudiantes de tecnología de UTEC para hacer webs, automatizaciones y apps, con el pago en garantía.
            Tu único trabajo es ayudar al cliente a definir su proyecto digital. Hablas como un consultor amable, \
            en español de Perú, simple y sin palabras técnicas.

            REGLAS:
            - Responde SOLO sobre el proyecto digital del cliente en DEVUTEC. Si el mensaje es de otro tema \
            (tareas, recetas, chistes, programación ajena, política, otra empresa, etc.), pon outOfScope = true.
            - Haz UNA pregunta a la vez. Averigua, en este orden y solo lo que falte: qué negocio tiene, qué quiere \
            lograr, qué necesita que tenga (páginas, carta, pedidos, reservas, pagos, etc.), si tiene logo o colores, \
            y para cuándo lo necesita.
            - No preguntes por tecnologías: el cliente no sabe de eso. Tú eliges las habilidades.
            - NUNCA escribas precios, montos ni rangos en message: DEVUTEC los calcula con datos reales y se los \
            muestra aparte. Si el cliente menciona su presupuesto, guárdalo en clientBudget (es el TOTAL que pagaría, \
            ya con la tarifa de DEVUTEC).
            - Usa SOLO los códigos de la tabla de funcionalidades y SOLO las habilidades de la lista.
            - Pon ready = true cuando sepas el tipo de negocio, qué quiere lograr y qué funcionalidades necesita. \
            Entonces llena title, clientDescription, category, skills y features, y en message dile que ya armaste \
            el proyecto y que lo revise.
            - Nunca pidas contraseñas, datos de tarjetas ni códigos de verificación.

            TABLA DE FUNCIONALIDADES (código: nombre · tipo):
            %s

            HABILIDADES DISPONIBLES:
            %s
            """;

    private final AiGateway aiGateway;
    private final CurrentUserService currentUserService;
    private final SkillRepository skillRepository;
    private final FuncionalidadReferenciaRepository funcionalidades;
    private final PriceEstimator priceEstimator;
    private final CommissionCalculator commission;
    private final PaymentPlanner paymentPlanner;
    private final int dailyLimit;
    private final Clock clock;
    // Mensajes usados hoy por cada cliente: "id:fecha" → cantidad. Se reinicia con el servidor; basta para la demo.
    private final Map<String, Integer> usage = new ConcurrentHashMap<>();

    @Autowired
    public ProjectAssistantService(AiGateway aiGateway, CurrentUserService currentUserService,
                                   SkillRepository skillRepository, FuncionalidadReferenciaRepository funcionalidades,
                                   PriceEstimator priceEstimator, CommissionCalculator commission,
                                   PaymentPlanner paymentPlanner,
                                   @Value("${assistant.daily-limit:40}") int dailyLimit) {
        this(aiGateway, currentUserService, skillRepository, funcionalidades, priceEstimator, commission, paymentPlanner,
                dailyLimit, Clock.systemDefaultZone());
    }

    ProjectAssistantService(AiGateway aiGateway, CurrentUserService currentUserService,
                            SkillRepository skillRepository, FuncionalidadReferenciaRepository funcionalidades,
                            PriceEstimator priceEstimator, CommissionCalculator commission, PaymentPlanner paymentPlanner,
                            int dailyLimit, Clock clock) {
        this.aiGateway = aiGateway;
        this.currentUserService = currentUserService;
        this.skillRepository = skillRepository;
        this.funcionalidades = funcionalidades;
        this.priceEstimator = priceEstimator;
        this.commission = commission;
        this.paymentPlanner = paymentPlanner;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    public ProjectAssistantResponseDTO chat(ProjectAssistantRequestDTO request) {
        User user = currentUserService.getCurrentUser();
        int remaining = useOneMessage(user.getId());

        List<ChatTurnDTO> turns = new ArrayList<>();
        if (request.history() != null) {
            // La conversación tiene que empezar con un mensaje del cliente
            request.history().stream().dropWhile(turn -> !"user".equals(turn.role())).forEach(turns::add);
        }
        turns.add(new ChatTurnDTO("user", request.message().trim()));

        List<Skill> skills = skillRepository.findAll();
        List<FuncionalidadReferencia> catalog = funcionalidades.findAllByOrderByCategoriaAscCodigoAsc();
        String instructions = RULES.formatted(
                catalog.stream().map(f -> "- " + f.getCodigo() + ": " + f.getNombre() + " · " + f.getCategoria())
                        .collect(Collectors.joining("\n")),
                skills.stream().map(Skill::getName).collect(Collectors.joining(", ")));

        ProjectAssistantAiReply reply;
        try {
            reply = aiGateway.replyStructured(instructions, turns, ProjectAssistantAiReply.class);
        } catch (RuntimeException e) {
            // Si la IA no respondió, ese mensaje no cuenta para el límite del día
            giveBackOneMessage(user.getId());
            throw e;
        }

        if (reply.outOfScope()) {
            return new ProjectAssistantResponseDTO(OUT_OF_SCOPE_REPLY, true, false, remaining, null);
        }
        String message = reply.message() == null || reply.message().isBlank()
                ? "¿Me cuentas un poco más sobre tu negocio?" : reply.message().trim();
        if (!reply.ready()) {
            return new ProjectAssistantResponseDTO(message, false, false, remaining, null);
        }
        return new ProjectAssistantResponseDTO(message, false, true, remaining, buildDraft(reply, skills));
    }

    ProjectAssistantResponseDTO.Draft buildDraft(ProjectAssistantAiReply reply, List<Skill> skills) {
        // Habilidades: solo las que existen en DEVUTEC (sin importar mayúsculas)
        Map<String, Skill> byName = skills.stream()
                .collect(Collectors.toMap(skill -> skill.getName().toLowerCase(), skill -> skill, (a, b) -> a));
        Set<Long> skillIds = new LinkedHashSet<>();
        List<String> skillNames = new ArrayList<>();
        if (reply.skills() != null) {
            for (String name : reply.skills()) {
                Skill skill = name == null ? null : byName.get(name.trim().toLowerCase());
                if (skill != null && skillIds.add(skill.getId())) skillNames.add(skill.getName());
            }
        }

        // Funcionalidades: PriceEstimator descarta los códigos que no están en la tabla
        Map<String, Integer> quantities = new LinkedHashMap<>();
        if (reply.features() != null) {
            for (ProjectAssistantAiReply.AiFeature feature : reply.features()) {
                if (feature != null && feature.code() != null && !feature.code().isBlank()) {
                    quantities.merge(feature.code().trim().toUpperCase(), Math.max(1, Math.min(20, feature.quantity())), Integer::sum);
                }
            }
        }
        List<FeatureQuantityDTO> requested = quantities.entrySet().stream()
                .map(entry -> new FeatureQuantityDTO(entry.getKey(), entry.getValue()))
                .toList();
        PriceEstimateDTO estimate = priceEstimator.estimate(skillIds, requested);
        List<FeatureQuantityDTO> features = estimate.features().stream()
                .map(feature -> new FeatureQuantityDTO(feature.code(), feature.quantity()))
                .toList();

        // Presupuesto: el cliente escribe el TOTAL (ya con la comisión). Si no lo dijo, se usa el medio del rango sugerido.
        BudgetBreakdownDTO budget = reply.clientBudget() > 0
                ? commission.fromClientTotal(BigDecimal.valueOf(reply.clientBudget()))
                : null;
        BigDecimal freelancerBase = budget != null
                ? budget.freelancerAmount()
                : estimate.freelancerMin().add(estimate.freelancerMax()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        List<ProjectAssistantResponseDTO.StageAmount> stages = estimate.stages().stream()
                .map(stage -> {
                    BigDecimal freelancerAmount = freelancerBase.multiply(BigDecimal.valueOf(stage.percent()))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    return new ProjectAssistantResponseDTO.StageAmount(stage.name(), stage.percent(), freelancerAmount,
                            commission.clientTotalOf(freelancerAmount), stage.hoursMin(), stage.hoursMax());
                })
                .toList();

        BigDecimal clientTotal = budget != null ? budget.clientTotal() : commission.clientTotalOf(freelancerBase);
        List<PaymentPlanner.InstallmentDTO> payments = paymentPlanner.plan(freelancerBase, clientTotal);

        int weeks = reply.weeks() > 0 ? reply.weeks() : estimate.weeksMax();
        String category = reply.category() != null && reply.category().matches("WEB|AUTOMATIZACION|APP")
                ? reply.category() : "WEB";
        String brandStyle = reply.brandStyle() == null ? "" : reply.brandStyle().trim();
        String description = reply.clientDescription() == null ? "" : reply.clientDescription().trim();
        if (!brandStyle.isEmpty()) description = description + "\n\nEstilo de la marca: " + brandStyle;

        return new ProjectAssistantResponseDTO.Draft(
                reply.title() == null || reply.title().isBlank() ? "Nuevo proyecto" : reply.title().trim(),
                description,
                category,
                skillIds,
                skillNames,
                features,
                LocalDate.now(clock).plusWeeks(weeks),
                brandStyle,
                estimate,
                budget,
                commission.getRate(),
                stages,
                payments);
    }

    private void giveBackOneMessage(Long userId) {
        usage.merge(userId + ":" + LocalDate.now(clock), -1, Integer::sum);
    }

    // Suma un mensaje al día de hoy y devuelve cuántos quedan; si ya no quedan, responde 429
    private int useOneMessage(Long userId) {
        String key = userId + ":" + LocalDate.now(clock);
        int used = usage.merge(key, 1, Integer::sum);
        if (used > dailyLimit) {
            usage.merge(key, -1, Integer::sum);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Llegaste al límite de mensajes de hoy con el asistente. Puedes crear tu proyecto con el formulario");
        }
        return dailyLimit - used;
    }
}
