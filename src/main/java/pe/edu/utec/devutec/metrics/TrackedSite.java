package pe.edu.utec.devutec.metrics;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utec.devutec.model.contract.Contract;

import java.time.LocalDateTime;

// La web que entrega el estudiante, conectada a las métricas de DEVUTEC.
// siteKey es pública (va en el script de la web): solo sirve para mandar visitas, no para leer nada.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tracked_sites")
public class TrackedSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, unique = true)
    private Contract contract;

    @Column(name = "site_key", nullable = false, unique = true, length = 32)
    private String siteKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
