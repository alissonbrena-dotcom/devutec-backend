package pe.edu.utec.devutec.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponseDTO {

    private Long id;
    private String title;
    private String description;
    private BigDecimal budget;
    private LocalDate deadline;
    private String location;
    private Long clientId;
    private String status;
    private Set<SkillResponseDTO> skills;
    private LocalDateTime createdAt;
    // Nombre del negocio y fecha de registro del cliente, para la tarjeta "Cliente" del detalle del proyecto
    private String clientName;
    private LocalDateTime clientSince;
    // Redes y página web del negocio del cliente, para que el freelancer vea su estilo antes de postular
    private SocialLinksDTO clientLinks;
    // El cliente pidió métricas DEVUTEC: el estudiante lo ve antes de postular ("Incluye métricas DEVUTEC")
    private boolean metricsRequested;
}