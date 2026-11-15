package pe.edu.utec.devutec.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.exceptions.ApiException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GeminiRestGatewayTest {

    @Test
    void reply_sinClave_respondeServicioNoDisponible() {
        GeminiRestGateway gateway = new GeminiRestGateway("", "gemini-3.5-flash-lite");

        ApiException error = assertThrows(ApiException.class,
                () -> gateway.reply("reglas", List.of(new ChatTurnDTO("user", "hola"))));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    @Test
    void extractText_juntaLasPartesDeLaPrimeraRespuesta() {
        Map<String, Object> response = Map.of("candidates", List.of(
                Map.of("content", Map.of("parts", List.of(Map.of("text", "Hola, "), Map.of("text", "soy Cuyi."))))));

        assertEquals("Hola, soy Cuyi.", GeminiRestGateway.extractText(response));
    }

    @Test
    void extractText_sinTexto_respondeBadGateway() {
        ApiException error = assertThrows(ApiException.class,
                () -> GeminiRestGateway.extractText(Map.of("candidates", List.of())));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
    }
}
