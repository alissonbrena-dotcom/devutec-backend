package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutConfirmRequestDTO(
        @NotBlank(message = "El sessionId de Stripe es obligatorio")
        String sessionId
) {
}
