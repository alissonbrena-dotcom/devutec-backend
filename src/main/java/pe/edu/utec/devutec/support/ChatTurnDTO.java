package pe.edu.utec.devutec.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Un mensaje anterior de la conversación: "user" (la persona) o "assistant" (el asistente)
public record ChatTurnDTO(
        @NotBlank @Pattern(regexp = "user|assistant", message = "El rol debe ser user o assistant") String role,
        @NotBlank @Size(max = 1500, message = "Cada mensaje puede tener hasta 1500 caracteres") String text
) {
}
