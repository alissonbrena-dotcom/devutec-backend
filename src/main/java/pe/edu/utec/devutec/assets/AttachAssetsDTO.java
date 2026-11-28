package pe.edu.utec.devutec.assets;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AttachAssetsDTO(
        @NotNull(message = "Elige los archivos")
        @Size(max = 7, message = "Se pueden adjuntar hasta 7 archivos")
        List<Long> assetIds
) {
}
