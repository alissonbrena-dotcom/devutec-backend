package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class ProjectUpdateDTO {

    @Size(min = 1, max = 255, message = "El título debe tener entre 1 y 255 caracteres")
    private String title;

    private String description;

    @Positive(message = "El presupuesto debe ser mayor a cero")
    private BigDecimal budget;

    private LocalDate deadline;

    @Size(max = 100, message = "La ubicación no puede tener más de 100 caracteres")
    private String location;

    private Set<Long> skillIds;
}