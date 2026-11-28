package pe.edu.utec.devutec.support;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.exceptions.ApiException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Llama a Claude (Anthropic) con su SDK oficial para Java. La clave solo vive en el servidor, en ANTHROPIC_API_KEY;
// nunca va al frontend ni al repositorio. El modelo se cambia con CLAUDE_MODEL sin tocar el código.
// Es el proveedor por defecto; con AI_PROVIDER=gemini se vuelve a usar GeminiRestGateway.
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "claude", matchIfMissing = true)
public class ClaudeGateway implements AiGateway {

    static final String REFUSAL_MESSAGE = "No puedo ayudarte con eso. Solo puedo ayudarte con tu proyecto en DEVUTEC";
    // Si el modelo rechaza una petición, Anthropic la reintenta en otro modelo (solo modelos que lo permiten)
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final String model;
    private final OutputConfig.Effort chatEffort;
    private final boolean fallbacks;

    public ClaudeGateway(@Value("${claude.api-key:}") String apiKey,
                         @Value("${claude.model}") String model,
                         @Value("${claude.chat-effort:low}") String chatEffort,
                         @Value("${claude.fallbacks:true}") boolean fallbacks) {
        this.model = model;
        this.chatEffort = OutputConfig.Effort.of(chatEffort.toLowerCase());
        this.fallbacks = fallbacks && supportsFallbacks(model);
        // Sin clave no se crea el cliente: el asistente responde "no disponible" en vez de romper el arranque
        this.client = apiKey == null || apiKey.isBlank() ? null : AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(45))
                .maxRetries(2)
                .build();
    }

    // Los reintentos del servidor ante un rechazo existen en Opus 5.5 / Opus 5 / Sonnet 5.5 / Fable, no en Haiku
    static boolean supportsFallbacks(String model) {
        return model.startsWith("claude-opus-5") || model.startsWith("claude-sonnet-5-5") || model.startsWith("claude-fable-5");
    }

    @Override
    public String reply(String instructions, List<ChatTurnDTO> turns) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(2048L)
                .system(instructions)
                // Respuestas cortas de ayuda: esfuerzo bajo, más rápido y más barato
                .outputConfig(OutputConfig.builder().effort(chatEffort).build());
        for (ChatTurnDTO turn : mergeConsecutive(turns)) {
            if ("assistant".equals(turn.role())) builder.addAssistantMessage(turn.text());
            else builder.addUserMessage(turn.text());
        }
        if (fallbacks) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        Message response = call(() -> client().messages().create(builder.build()));
        checkStop(response.stopReason());
        StringBuilder text = new StringBuilder();
        response.content().forEach(block -> block.text().ifPresent(t -> text.append(t.text())));
        if (text.isEmpty()) throw new ApiException(HttpStatus.BAD_GATEWAY, "El asistente no pudo responder. Intenta de nuevo");
        return text.toString().trim();
    }

    @Override
    public <T> T replyStructured(String instructions, List<ChatTurnDTO> turns, Class<T> type) {
        StructuredMessageCreateParams.Builder<T> builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096L)
                .system(instructions)
                .outputConfig(type);
        for (ChatTurnDTO turn : mergeConsecutive(turns)) {
            if ("assistant".equals(turn.role())) builder.addAssistantMessage(turn.text());
            else builder.addUserMessage(turn.text());
        }
        if (fallbacks) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        StructuredMessage<T> response = call(() -> client().messages().create(builder.build()));
        checkStop(response.stopReason());
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_GATEWAY, "El asistente no pudo responder. Intenta de nuevo"));
    }

    @Override
    public <T> T replyStructuredWithImages(String instructions, String text, List<ImageInput> images, Class<T> type) {
        List<ContentBlockParam> blocks = new java.util.ArrayList<>();
        for (ImageInput image : images) {
            blocks.add(ContentBlockParam.ofImage(Base64ImageSource.builder()
                    .mediaType(Base64ImageSource.MediaType.of(image.mediaType()))
                    .data(java.util.Base64.getEncoder().encodeToString(image.data()))
                    .build()));
        }
        blocks.add(ContentBlockParam.ofText(text));
        StructuredMessageCreateParams.Builder<T> builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system(instructions)
                .outputConfig(type)
                .addUserMessageOfContentBlockParams(blocks);
        if (fallbacks) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        StructuredMessage<T> response = call(() -> client().messages().create(builder.build()));
        checkStop(response.stopReason());
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo analizar el archivo"));
    }

    private AnthropicClient client() {
        if (client == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El asistente no está disponible en este momento");
        }
        return client;
    }

    private static void checkStop(Optional<StopReason> stopReason) {
        if (stopReason.isPresent() && StopReason.REFUSAL.equals(stopReason.get())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, REFUSAL_MESSAGE);
        }
        if (stopReason.isPresent() && StopReason.MAX_TOKENS.equals(stopReason.get())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "La respuesta del asistente quedó incompleta. Intenta de nuevo");
        }
    }

    private static <R> R call(java.util.function.Supplier<R> request) {
        try {
            return request.get();
        } catch (RateLimitException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El asistente está muy ocupado. Intenta en un rato");
        } catch (AnthropicServiceException ex) {
            log.warn("Claude respondió con un error {}: {}", ex.statusCode(), ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "El asistente no pudo responder. Intenta de nuevo");
        } catch (AnthropicException ex) {
            log.warn("No se pudo conectar con Claude: {}", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "El asistente no pudo responder. Intenta de nuevo");
        }
    }

    // Claude espera que los mensajes alternen entre la persona y el asistente: se juntan los seguidos del mismo rol
    static List<ChatTurnDTO> mergeConsecutive(List<ChatTurnDTO> turns) {
        List<ChatTurnDTO> merged = new ArrayList<>();
        for (ChatTurnDTO turn : turns) {
            String role = "assistant".equals(turn.role()) ? "assistant" : "user";
            if (!merged.isEmpty() && merged.get(merged.size() - 1).role().equals(role)) {
                ChatTurnDTO last = merged.remove(merged.size() - 1);
                merged.add(new ChatTurnDTO(role, last.text() + "\n\n" + turn.text()));
            } else {
                merged.add(new ChatTurnDTO(role, turn.text()));
            }
        }
        return merged;
    }
}
