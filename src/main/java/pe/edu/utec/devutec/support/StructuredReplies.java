package pe.edu.utec.devutec.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.exceptions.ApiException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

// Pide a la IA una respuesta con forma fija (la clase type).
// Primero usa replyStructured (salidas estructuradas, lo más confiable). Si el proveedor no lo soporta
// (por ejemplo Gemini en local, que responde 503), pide texto con reply y lee el JSON. Por eso las instrucciones
// deben incluir el formato JSON con los mismos nombres de campo que la clase.
@Slf4j
public final class StructuredReplies {

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private StructuredReplies() {
    }

    public static <T> T ask(AiGateway ai, String instructions, List<ChatTurnDTO> turns, Class<T> type) {
        try {
            return ai.replyStructured(instructions, turns, type);
        } catch (ApiException e) {
            if (e.getStatus() != HttpStatus.SERVICE_UNAVAILABLE) throw e;
        }
        return parse(ai.reply(instructions, turns), type);
    }

    // Lee el JSON aunque venga dentro de ```json ... ``` o con texto alrededor
    public static <T> T parse(String reply, Class<T> type) {
        if (reply != null) {
            int start = reply.indexOf('{');
            int end = reply.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return JSON.readValue(reply.substring(start, end + 1), type);
                } catch (JacksonException e) {
                    log.warn("La IA respondió un JSON que no se pudo leer: {}", e.getOriginalMessage());
                }
            }
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Cuyi no pudo responder. Intenta de nuevo");
    }
}
