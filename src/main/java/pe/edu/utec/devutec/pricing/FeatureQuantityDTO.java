package pe.edu.utec.devutec.pricing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Una funcionalidad del proyecto y cuántas veces aparece (por ejemplo 3 páginas informativas)
public record FeatureQuantityDTO(
        @NotBlank @Size(max = 40) String code,
        @Min(1) @Max(20) int quantity
) {
}
