package pe.edu.utec.devutec.model.contract;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Una etapa del contrato. El trabajo se entrega y se aprueba etapa por etapa, en orden (position).
@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "contract_stages")
public class ContractStage {

    // Rondas de cambios que el cliente puede pedir en cada etapa
    public static final int MAX_CHANGE_ROUNDS = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    // Orden de la etapa dentro del contrato: 1, 2, 3...
    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private String name;

    // Porcentaje del monto del contrato que vale esta etapa
    @Column(nullable = false)
    private Integer percentage;

    @Column(nullable = false)
    private BigDecimal amount;

    // En qué pago del cliente entra esta etapa (1 o 2). Lo usa el pago en dos partes.
    @Column(name = "payment_part", nullable = false)
    private Integer paymentPart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StageStatus status;

    // Fecha sugerida para entregar la etapa, repartida según el plazo acordado. null si no hay plazo.
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "delivery_url")
    private String deliveryUrl;

    @Column(name = "delivery_note", length = 1000)
    private String deliveryNote;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "change_rounds", nullable = false)
    private Integer changeRounds = 0;

    // Lo último que pidió cambiar el cliente
    @Column(name = "change_request_note", length = 1000)
    private String changeRequestNote;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
