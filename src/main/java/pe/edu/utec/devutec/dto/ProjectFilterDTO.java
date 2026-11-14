package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.utec.devutec.model.ProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class ProjectFilterDTO {

    private String search;

    private ProjectStatus status;

    private Long skillId;

    // Varias habilidades a la vez: ?skillIds=1,2 trae los proyectos que piden al menos una de ellas
    private List<Long> skillIds;

    @PositiveOrZero(message = "minBudget no puede ser negativo")
    private BigDecimal minBudget;

    @PositiveOrZero(message = "maxBudget no puede ser negativo")
    private BigDecimal maxBudget;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deadlineBefore;

    // Junta skillId (un solo valor, como antes) y skillIds en un conjunto sin repetidos ni nulos
    public Set<Long> allSkillIds() {
        Set<Long> ids = new LinkedHashSet<>();
        if (skillId != null) ids.add(skillId);
        if (skillIds != null) skillIds.stream().filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }
}
