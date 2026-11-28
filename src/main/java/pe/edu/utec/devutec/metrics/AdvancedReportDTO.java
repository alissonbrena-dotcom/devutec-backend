package pe.edu.utec.devutec.metrics;

import java.util.List;

// Reporte Pro: sale de Amplitude. available=false con un mensaje si no hay plan Pro o si Amplitude no respondió
public record AdvancedReportDTO(
        boolean available,
        String message,
        List<Item> topPages,
        List<Item> sources,
        List<Item> devices,
        List<Item> contactsByMonth
) {
    public record Item(String label, long total) {
    }

    static AdvancedReportDTO unavailable(String message) {
        return new AdvancedReportDTO(false, message, List.of(), List.of(), List.of(), List.of());
    }
}
