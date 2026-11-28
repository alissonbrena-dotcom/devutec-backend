package pe.edu.utec.devutec.verification;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Decisión del admin: aprobar o rechazar, con el motivo si se rechaza
public record CarnetReviewRequestDTO(
        @NotNull(message = "Indica si apruebas el carnet") Boolean approved,
        @Size(max = 200, message = "El motivo puede tener hasta 200 caracteres") String reason
) {
}
