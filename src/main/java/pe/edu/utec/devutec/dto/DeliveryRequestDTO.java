package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Lo que manda el freelancer al entregar: el link a su trabajo (Drive, GitHub, Figma, una web publicada...)
// y, si quiere, una nota para el cliente
public record DeliveryRequestDTO(
        @NotBlank(message = "Pega el link de tu trabajo")
        @Size(max = 255, message = "El link de entrega es demasiado largo")
        String link,
        @Size(max = 1000, message = "La nota no puede pasar de 1000 caracteres")
        String nota
) {
}
