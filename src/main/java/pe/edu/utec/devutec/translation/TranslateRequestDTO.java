package pe.edu.utec.devutec.translation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TranslateRequestDTO(
        @NotNull(message = "Indica hacia quién se traduce: FOR_CLIENT o FOR_STUDENT")
        TranslationDirection direction,

        // Si no se envía y es FOR_CLIENT, se traduce la nota de la última entrega del contrato
        @Size(max = 2000, message = "El mensaje puede tener hasta 2000 caracteres")
        String text,

        // Etapa del mensaje, para darle contexto a la IA (por ejemplo «Diseño»)
        @Size(max = 60, message = "El nombre de la etapa puede tener hasta 60 caracteres")
        String stageName
) {
}
