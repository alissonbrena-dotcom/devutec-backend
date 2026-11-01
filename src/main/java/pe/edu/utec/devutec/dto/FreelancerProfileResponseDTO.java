package pe.edu.utec.devutec.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerProfileResponseDTO {
    private Long userId;
    private String nombre;
    private String universidad;
    private Integer ciclo;
    private Boolean egresado;
    private BigDecimal tarifaHora;
    private String portafolioUrl;
    private String githubUsername;
    private BigDecimal calificacionPromedio;
}
