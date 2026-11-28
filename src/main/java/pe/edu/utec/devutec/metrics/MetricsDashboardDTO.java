package pe.edu.utec.devutec.metrics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Panel de métricas de la web del cliente en los últimos N días
public record MetricsDashboardDTO(
        boolean webProject,
        boolean installed,
        LocalDateTime lastEventAt,
        int days,
        String summary,
        long visits,
        long visitors,
        long orders,
        Integer averageLoadMs,
        List<Day> daily,
        List<Count> topPages,
        List<Count> topEvents
) {
    public record Day(LocalDate date, long visits, long orders) {
    }

    public record Count(String name, long count) {
    }
}
