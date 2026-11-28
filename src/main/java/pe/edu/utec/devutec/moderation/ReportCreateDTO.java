package pe.edu.utec.devutec.moderation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Reportar a la otra persona de un contrato o de una postulación. Se manda contractId O applicationId.
public record ReportCreateDTO(
        Long contractId,
        Long applicationId,
        @NotNull(message = "Elige el motivo del reporte") ReportReason reason,
        @Size(max = 1000, message = "El detalle puede tener hasta 1000 caracteres") String detail
) {
}
