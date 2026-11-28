package pe.edu.utec.devutec.quality;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// El link de la web publicada que se va a revisar
public record QualityCheckRequestDTO(
        @NotBlank(message = "Pega el link de tu web publicada")
        @Size(max = 500, message = "El link puede tener hasta 500 caracteres")
        String url
) {
}
