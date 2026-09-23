package pe.edu.utec.devutec.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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

    private BigDecimal tarifaHora;

    private String portafolioUrl;

    private BigDecimal calificacionPromedio;

}
