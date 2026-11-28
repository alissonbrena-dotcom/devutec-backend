package pe.edu.utec.devutec.model.contract;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.application.Application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "contracts")
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // Lo que entregó el freelancer en la etapa actual: el link a su trabajo y una nota opcional para el cliente
    @Column(name = "delivery_url")
    private String deliveryUrl;

    @Column(name = "delivery_note", length = 1000)
    private String deliveryNote;

    // Por qué se cerró el contrato antes de terminar, por ejemplo PAGO_2_NO_REALIZADO. null si no se cerró así.
    @Column(name = "closed_reason", length = 40)
    private String closedReason;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // Etapas del trabajo, en orden. Se guardan y se borran junto con el contrato.
    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ContractStage> stages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ContractStatus.IN_PROGRESS;
        }
    }

    public void addStage(ContractStage stage) {
        stage.setContract(this);
        stages.add(stage);
    }
}
