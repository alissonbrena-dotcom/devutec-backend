package pe.edu.utec.devutec.metrics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class MetricsSubscriptionDTOs {

    private MetricsSubscriptionDTOs() {
    }

    public record StartRequest(@NotNull(message = "Elige Básico o Pro") MetricsPlan plan) {
    }

    public record StartResponse(String sessionId, String checkoutUrl) {
    }

    public record ConfirmRequest(@NotBlank(message = "Falta la sesión de pago") String sessionId) {
    }
}
