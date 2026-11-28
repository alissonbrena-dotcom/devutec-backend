package pe.edu.utec.devutec.dto;

import java.util.List;
import pe.edu.utec.devutec.pricing.FeatureQuantityDTO;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
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

    @Size(max = 100, message = "La ubicación no puede tener más de 100 caracteres")
    private String location;

    private Set<Long> skillIds;

    // Opcionales: los llena el asistente IA. Tipo de proyecto (WEB, AUTOMATIZACION o APP) y funcionalidades
    // con códigos de la tabla de horas de referencia, para que el estudiante vea las horas estimadas.
    @Pattern(regexp = "WEB|AUTOMATIZACION|APP", message = "El tipo de proyecto debe ser WEB, AUTOMATIZACION o APP")
    private String category;

    @Size(max = 30, message = "Se pueden enviar hasta 30 funcionalidades")
    private List<@Valid FeatureQuantityDTO> features;

    // true si el cliente quiere métricas DEVUTEC de su web (asistente o casilla del formulario). null = no
    private Boolean metricsRequested;

    // Constructor sin los campos del asistente (los proyectos creados a mano no los tienen)
    public ProjectCreateDTO(String title, String description, BigDecimal budget, LocalDate deadline,
                            String location, Set<Long> skillIds) {
        this(title, description, budget, deadline, location, skillIds, null, null, null);
    }
}