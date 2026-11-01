package pe.edu.utec.devutec.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerProfileUpdateDTO {

    @Size(max = 255, message = "La universidad no puede tener más de 255 caracteres")
    private String universidad;

    @Min(value = 1, message = "El ciclo debe estar entre 1 y 10")
    @Max(value = 10, message = "El ciclo debe estar entre 1 y 10")
    private Integer ciclo;

    private Boolean egresado;

    @Positive(message = "La tarifa por hora debe ser mayor a cero")
    private BigDecimal tarifaHora;

    @URL(message = "La URL del portafolio no es válida")
    @Size(max = 255, message = "La URL del portafolio no puede tener más de 255 caracteres")
    private String portafolioUrl;

    @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$",
            message = "El usuario de GitHub solo puede tener letras, números y guiones (máximo 39 caracteres)")
    private String githubUsername;

    @JsonIgnore
    @AssertTrue(message = "Un egresado no puede indicar ciclo")
    public boolean isCicloValidoParaEgresado() {
        return !(Boolean.TRUE.equals(egresado) && ciclo != null);
    }
}
