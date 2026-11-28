package pe.edu.utec.devutec.metrics;

import java.time.LocalDateTime;

// «Cómo integrarlo»: el código que el estudiante pega en la web y si ya están llegando datos
public record MetricsSetupDTO(
        boolean webProject,
        String siteKey,
        String scriptUrl,
        String snippet,
        String orderSnippet,
        boolean installed,
        LocalDateTime lastEventAt
) {
}
