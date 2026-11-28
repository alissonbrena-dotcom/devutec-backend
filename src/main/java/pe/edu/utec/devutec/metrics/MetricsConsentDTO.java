package pe.edu.utec.devutec.metrics;

import jakarta.validation.constraints.AssertTrue;

public record MetricsConsentDTO(
        @AssertTrue(message = "Tienes que aceptar el uso de métricas anónimas para activarlas")
        boolean consent
) {
}
