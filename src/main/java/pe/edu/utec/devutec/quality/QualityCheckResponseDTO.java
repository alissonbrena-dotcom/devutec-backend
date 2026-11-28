package pe.edu.utec.devutec.quality;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Resultado de la revisión de calidad para la web y la app.
// failures: lo que no cumple el estándar (bloquea la entrega). issues: lo que Google sugiere mejorar.
// tasks y forClient: de la IA, solo cuando no pasa.
public record QualityCheckResponseDTO(
        Long id,
        Long stageId,
        String url,
        QualityStatus status,
        List<Score> scores,
        Boolean https,
        List<String> failures,
        List<PageSpeedReport.Issue> issues,
        List<String> tasks,
        String forClient,
        LocalDate dueDate,
        LocalDateTime createdAt
) {
    // Un puntaje con su mínimo: «Rendimiento 42 / mínimo 50»
    public record Score(String key, String label, Integer value, int minimum, boolean passed) {
    }
}
