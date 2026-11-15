package pe.edu.utec.devutec.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequestDTO(
        @NotBlank(message = "Escribe tu pregunta")
        @Size(max = 500, message = "La pregunta puede tener hasta 500 caracteres")
        String message,

        // Últimos mensajes de la conversación, para que el asistente entienda el contexto
        @Size(max = 10, message = "Se pueden enviar hasta 10 mensajes anteriores")
        List<@Valid ChatTurnDTO> history
) {
}
