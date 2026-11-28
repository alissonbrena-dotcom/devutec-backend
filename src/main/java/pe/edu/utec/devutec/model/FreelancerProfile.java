package pe.edu.utec.devutec.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import pe.edu.utec.devutec.auth.domain.User;

import java.math.BigDecimal;

@Entity
@Table(name="freelancer_profiles")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id", nullable = false, unique = true)
    private User user;

    private String universidad;

    private Integer ciclo;

    private Boolean egresado;

    private BigDecimal tarifaHora;

    private String portafolioUrl;

    private String githubUsername;

    private BigDecimal calificacionPromedio;

    // "Sobre mí" del perfil público: cómo trabaja y qué hace bien (opcional, máximo 500 caracteres)
    @Column(length = 500)
    private String presentacion;

    // Segunda verificación: el carnet universitario revisado por el admin (null si no lo subió)
    @Enumerated(EnumType.STRING)
    @Column(name = "carnet_status", length = 12)
    private pe.edu.utec.devutec.verification.CarnetStatus carnetStatus;

}
