package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Celular para que la otra persona de un contrato pueda escribir por WhatsApp. Se valida y limpia en PhoneNumbers.
public record PhoneUpdateDTO(
        @NotBlank(message = "Escribe tu celular")
        @Size(max = 20, message = "El celular es demasiado largo")
        String celular
) {
}
