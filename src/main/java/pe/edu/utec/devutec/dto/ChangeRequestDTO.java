package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Lo que manda el cliente al pedir cambios en la etapa entregada
public record ChangeRequestDTO(
        @NotBlank(message = "Cuéntale al freelancer qué quieres cambiar")
        @Size(max = 1000, message = "El pedido de cambios no puede pasar de 1000 caracteres")
        String nota
) {
}
