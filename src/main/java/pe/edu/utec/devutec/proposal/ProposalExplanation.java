package pe.edu.utec.devutec.proposal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utec.devutec.model.application.Application;

import java.time.LocalDateTime;

// Explicación de la IA de una contraoferta, guardada para no volver a pedirla.
// inputHash cambia si cambian el precio, el plazo o el mensaje: ahí se vuelve a explicar.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "proposal_explanations")
public class ProposalExplanation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    // JSON de ProposalAiReply
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    void touch() {
        createdAt = LocalDateTime.now();
    }
}
