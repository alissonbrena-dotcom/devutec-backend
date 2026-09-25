package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.utec.devutec.model.ProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ProjectFilterDTO {

    private String search;

    private ProjectStatus status;

    private Long skillId;

    @PositiveOrZero(message = "minBudget no puede ser negativo")
    private BigDecimal minBudget;

    @PositiveOrZero(message = "maxBudget no puede ser negativo")
    private BigDecimal maxBudget;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deadlineBefore;
}