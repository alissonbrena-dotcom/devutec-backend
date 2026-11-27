package pe.edu.utec.devutec.translation;

import java.time.LocalDateTime;

public record TranslationResponseDTO(
        Long id,
        TranslationDirection direction,
        String stageName,
        // El mensaje tal como lo escribió la otra persona: siempre se puede ver
        String original,
        TranslationContent result,
        LocalDateTime createdAt
) {
}
