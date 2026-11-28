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
    // Ruta de la foto de perfil o null si no tiene
    private String avatarUrl;
    // "Sobre mí" del perfil o null si no escribió nada
    private String presentacion;
    // Verificaciones: se registró con correo universitario (.edu.pe) y el admin aprobó su carnet
    private Boolean universityEmail;
    private Boolean carnetVerified;
}
