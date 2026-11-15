package pe.edu.utec.devutec.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupportChatServiceTest {

    // IA falsa: guarda lo que recibió y responde siempre lo mismo
    static class FakeAi implements AiGateway {
        String instructions;
        List<ChatTurnDTO> turns = new ArrayList<>();

        @Override
        public String reply(String instructions, List<ChatTurnDTO> turns) {
            this.instructions = instructions;
            this.turns = turns;
            return "Respuesta de prueba";
        }
    }

    private final FakeAi ai = new FakeAi();
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneId.of("America/Lima"));

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(7L);
        user.setRol(Role.CLIENT);
        when(currentUser.getCurrentUser()).thenReturn(user);
    }

    private SupportChatService service(int limit) {
        return new SupportChatService(ai, currentUser, limit, clock, "INFO DE DEVUTEC");
    }

    @Test
    void ask_mandaLaInformacionYElRolYDevuelveLaRespuesta() {
        ChatResponseDTO response = service(30).ask(new ChatRequestDTO("  ¿Cómo pago?  ", null));

        assertEquals("Respuesta de prueba", response.reply());
        assertEquals(29, response.remaining());
        assertTrue(ai.instructions.contains("INFO DE DEVUTEC"));
        assertTrue(ai.instructions.contains("CLIENT"));
        assertEquals(List.of(new ChatTurnDTO("user", "¿Cómo pago?")), ai.turns);
    }

    @Test
    void ask_conHistorial_loMandaEmpezandoPorUnMensajeDeLaPersona() {
        List<ChatTurnDTO> history = List.of(
                new ChatTurnDTO("assistant", "¡Hola! ¿En qué te ayudo?"),
                new ChatTurnDTO("user", "¿Cómo postulo?"),
                new ChatTurnDTO("assistant", "Desde Explorar."));

        service(30).ask(new ChatRequestDTO("¿Y luego?", history));

        assertEquals(3, ai.turns.size());
        assertEquals("user", ai.turns.get(0).role());
        assertEquals("¿Y luego?", ai.turns.get(2).text());
    }

    @Test
    void ask_alPasarElLimiteDelDia_respondeTooManyRequests() {
        SupportChatService service = service(2);
        service.ask(new ChatRequestDTO("uno", null));
        ChatResponseDTO second = service.ask(new ChatRequestDTO("dos", null));
        assertEquals(0, second.remaining());

        ApiException error = assertThrows(ApiException.class, () -> service.ask(new ChatRequestDTO("tres", null)));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
    }

    @Test
    void ask_siLaIaFalla_noDescuentaLaPregunta() {
        // IA que falla las dos primeras veces y después responde
        int[] calls = {0};
        AiGateway flaky = (instructions, turns) -> {
            if (++calls[0] <= 2) throw new ApiException(HttpStatus.BAD_GATEWAY, "falló");
            return "ok";
        };
        SupportChatService service = new SupportChatService(flaky, currentUser, 1, clock, "INFO");

        assertThrows(ApiException.class, () -> service.ask(new ChatRequestDTO("uno", null)));
        assertThrows(ApiException.class, () -> service.ask(new ChatRequestDTO("dos", null)));

        // Con límite 1, si las fallidas contaran ya sería 429; como no cuentan, esta sí responde
        ChatResponseDTO response = service.ask(new ChatRequestDTO("tres", null));
        assertEquals("ok", response.reply());
        assertEquals(0, response.remaining());
    }
}
