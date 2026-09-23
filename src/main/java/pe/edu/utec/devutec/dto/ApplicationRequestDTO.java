package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationRequestDTO {
    @NotNull(message = "El proyecto es obligatorio")
    private Long projectId;

    @NotBlank(message = "El mensaje es obligatorio")
    @Size(min = 20, max = 2000, message = "El mensaje debe tener entre 20 y 2000 caracteres")
    private String message;

    @NotNull(message = "El precio propuesto es obligatorio")
    @Positive(message = "El precio propuesto debe ser mayor a cero")
    private BigDecimal proposedPrice;
}
