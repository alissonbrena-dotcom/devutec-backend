package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateDTO {

    @NotBlank(message = "El título es obligatorio")
    private String title;

    private String description;

    @NotNull(message = "El presupuesto es obligatorio")
    @Positive(message = "El presupuesto debe ser mayor a cero")
    private BigDecimal budget;

    private LocalDate deadline;

    private Set<Long> skillIds;
}