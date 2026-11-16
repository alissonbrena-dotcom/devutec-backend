package pe.edu.utec.devutec.auth.dto;

import jakarta.validation.constraints.NotBlank;

// Token de Google con el que alguien que ya inició sesión confirma que es el dueño de la cuenta
public record GoogleConfirmRequest(
        @NotBlank(message = "El token de Google es obligatorio") String idToken
) {
}
