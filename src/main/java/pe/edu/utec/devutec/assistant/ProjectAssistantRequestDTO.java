package pe.edu.utec.devutec.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.edu.utec.devutec.support.ChatTurnDTO;

import java.util.List;

public record ProjectAssistantRequestDTO(
        @NotBlank(message = "Escribe un mensaje")
        @Size(max = 800, message = "El mensaje puede tener hasta 800 caracteres")
        String message,

        // La conversación hasta ahora, para que el asistente recuerde lo que ya le contaste
        @Size(max = 20, message = "Se pueden enviar hasta 20 mensajes anteriores")
        List<@Valid ChatTurnDTO> history
) {
}
