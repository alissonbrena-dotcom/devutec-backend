package pe.edu.utec.devutec.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pe.edu.utec.devutec.exceptions.ApiException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Llama a la API de Gemini (Google AI Studio). La clave solo vive en el servidor, en GEMINI_API_KEY;
// nunca va al frontend ni al repositorio. El modelo por defecto se define solo en application.properties
// y en el servidor se cambia con GEMINI_MODEL, sin tocar el código.
@Slf4j
@Component
public class GeminiRestGateway implements AiGateway {

    private static final String GEMINI_API = "https://generativelanguage.googleapis.com/v1beta";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    public GeminiRestGateway(@Value("${gemini.api-key:}") String apiKey,
                             @Value("${gemini.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        // Si Gemini tarda, se corta a los 25 segundos en vez de dejar la petición colgada
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));
        this.restClient = RestClient.builder().baseUrl(GEMINI_API).requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String reply(String instructions, List<ChatTurnDTO> turns) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El asistente no está disponible en este momento");
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instructions))),
                "contents", turns.stream()
                        .map(turn -> Map.of(
                                "role", "assistant".equals(turn.role()) ? "model" : "user",
                                "parts", List.of(Map.of("text", turn.text()))))
                        .toList(),
                "generationConfig", Map.of("temperature", 0.3, "maxOutputTokens", 1024)
        );
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return extractText(response);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El asistente está muy ocupado. Intenta en un rato");
        } catch (RestClientException ex) {
            log.warn("Gemini respondió con un error: {}", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "El asistente no pudo responder. Intenta de nuevo");
        }
    }

    // La respuesta viene en candidates[0].content.parts[*].text
    static String extractText(Map<?, ?> response) {
        if (response != null && response.get("candidates") instanceof List<?> candidates && !candidates.isEmpty()
                && candidates.get(0) instanceof Map<?, ?> candidate
                && candidate.get("content") instanceof Map<?, ?> content
                && content.get("parts") instanceof List<?> parts) {
            StringBuilder text = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> map && map.get("text") instanceof String piece) text.append(piece);
            }
            if (!text.isEmpty()) return text.toString().trim();
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "El asistente no pudo responder. Intenta de nuevo");
    }
}
