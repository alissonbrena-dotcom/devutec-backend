package pe.edu.utec.devutec.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Asistente de ayuda de DEVUTEC. Responde solo con la información de support/devutec-faq.md,
// y cada persona tiene un límite de preguntas por día para no gastar la cuota gratuita de Gemini.
@Service
public class SupportChatService {

    private static final String RULES = """
            Eres Cuyi, el asistente de ayuda de DEVUTEC. Responde en español, en tono cercano y claro, \
            en un máximo de 4 oraciones o una lista corta de pasos.
            Responde SOLO con la información de DEVUTEC que está abajo. Si la pregunta no es sobre DEVUTEC, \
            la respuesta no está en esa información o la persona tiene un problema que no puedes resolver \
            (un error, un pago, su cuenta), dilo con amabilidad y comparte el correo de soporte: %s. \
            No inventes precios, plazos, funciones ni políticas.
            Nunca pidas contraseñas, datos de tarjetas ni códigos de verificación.
            La persona que pregunta tiene el rol: %s.

            %s
            """;

    // Correo al que Cuyi deriva lo que no puede resolver
    static final String SUPPORT_EMAIL = "devutec.notificaciones@gmail.com";

    private final AiGateway aiGateway;
    private final CurrentUserService currentUserService;
    private final Clock clock;
    private final int dailyLimit;
    private final String knowledge;
    // Preguntas usadas hoy por cada usuario: "id:fecha" → cantidad. Se reinicia con el servidor, y basta para la demo.
    private final Map<String, Integer> usage = new ConcurrentHashMap<>();

    // Spring usa este constructor; el otro es para las pruebas (reloj y conocimiento fijos)
    @Autowired
    public SupportChatService(AiGateway aiGateway,
                              CurrentUserService currentUserService,
                              @Value("${support.chat.daily-limit:30}") int dailyLimit) {
        this(aiGateway, currentUserService, dailyLimit, Clock.systemDefaultZone(), loadKnowledge());
    }

    SupportChatService(AiGateway aiGateway, CurrentUserService currentUserService, int dailyLimit,
                       Clock clock, String knowledge) {
        this.aiGateway = aiGateway;
        this.currentUserService = currentUserService;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
        this.knowledge = knowledge;
    }

    public ChatResponseDTO ask(ChatRequestDTO request) {
        User user = currentUserService.getCurrentUser();
        int remaining = useOneQuestion(user.getId());

        List<ChatTurnDTO> turns = new ArrayList<>();
        if (request.history() != null) {
            // Gemini necesita que la conversación empiece con un mensaje de la persona
            request.history().stream()
                    .dropWhile(turn -> !"user".equals(turn.role()))
                    .forEach(turns::add);
        }
        turns.add(new ChatTurnDTO("user", request.message().trim()));

        String instructions = RULES.formatted(SUPPORT_EMAIL, user.getRol().name(), knowledge);
        try {
            return new ChatResponseDTO(aiGateway.reply(instructions, turns), remaining);
        } catch (RuntimeException e) {
            // Si la IA no respondió, esa pregunta no cuenta para el límite del día
            giveBackOneQuestion(user.getId());
            throw e;
        }
    }

    private void giveBackOneQuestion(Long userId) {
        usage.merge(userId + ":" + LocalDate.now(clock), -1, Integer::sum);
    }

    // Suma una pregunta al día de hoy y devuelve cuántas quedan; si ya no quedan, responde 429
    private int useOneQuestion(Long userId) {
        String key = userId + ":" + LocalDate.now(clock);
        int used = usage.merge(key, 1, Integer::sum);
        if (used > dailyLimit) {
            usage.merge(key, -1, Integer::sum);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Llegaste al límite de preguntas de hoy. Revisa las preguntas frecuentes o vuelve mañana");
        }
        return dailyLimit - used;
    }

    private static String loadKnowledge() {
        try (var input = new ClassPathResource("support/devutec-faq.md").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer support/devutec-faq.md", e);
        }
    }
}
