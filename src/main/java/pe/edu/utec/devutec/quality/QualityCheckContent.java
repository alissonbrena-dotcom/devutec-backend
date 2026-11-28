package pe.edu.utec.devutec.quality;

import java.util.List;

// Lo que se guarda como JSON en QualityCheck.content
public record QualityCheckContent(
        List<String> failures,
        List<PageSpeedReport.Issue> issues,
        List<String> tasks,
        String forClient
) {
}
