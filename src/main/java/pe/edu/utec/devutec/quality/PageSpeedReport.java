package pe.edu.utec.devutec.quality;

import java.util.List;

// Lo que importa del informe de PageSpeed Insights (celular). Los puntajes van de 0 a 100.
public record PageSpeedReport(
        int performance,
        int accessibility,
        int bestPractices,
        int seo,
        boolean https,
        List<Issue> issues
) {
    // Un punto a mejorar, con el título y el detalle que da Google (en español)
    public record Issue(String category, String title, String detail) {
    }
}
