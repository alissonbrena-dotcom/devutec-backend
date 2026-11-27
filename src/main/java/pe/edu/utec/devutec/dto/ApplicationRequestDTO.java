package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    // Contraoferta de monto: puede ser distinto al presupuesto del proyecto
    @NotNull(message = "El precio propuesto es obligatorio")
    @Positive(message = "El precio propuesto debe ser mayor a cero")
    private BigDecimal proposedPrice;

    // Contraoferta de plazo (opcional): si no se manda, se usa la fecha límite del proyecto
    @FutureOrPresent(message = "El plazo propuesto no puede ser una fecha pasada")
    private LocalDate proposedDeadline;

    // Postulación sin contraoferta de plazo
    public ApplicationRequestDTO(Long projectId, String message, BigDecimal proposedPrice) {
        this(projectId, message, proposedPrice, null);
    }
}
