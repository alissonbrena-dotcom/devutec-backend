package pe.edu.utec.devutec.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pe.edu.utec.devutec.model.contract.Contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Una cuota del pago en dos partes. El cliente paga cada cuota con tarjeta y queda retenida (HELD)
// hasta que se aprueban las etapas que cubre. Los montos ya separan lo del estudiante y la comisión.
@Getter
@Setter
@Entity
@Table(name = "payment_installments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"contract_id", "number"}))
public class PaymentInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    // 1 = al aceptar, 2 = al aprobar Diseño
    @Column(nullable = false)
    private Integer number;

    @Column(nullable = false)
    private String label;

    // Lo que paga el cliente en esta cuota (incluye la comisión)
    @Column(name = "client_amount", nullable = false)
    private BigDecimal clientAmount;

    // La parte del estudiante (clientAmount / 1.10)
    @Column(name = "student_amount", nullable = false)
    private BigDecimal studentAmount;

    // La comisión de DEVUTEC en esta cuota
    @Column(name = "commission_amount", nullable = false)
    private BigDecimal commissionAmount;

    // Bolsa de emergencia: parte del estudiante que se guarda hasta el final (solo en la cuota 1)
    @Column(name = "reserve_amount", nullable = false)
    private BigDecimal reserveAmount = BigDecimal.ZERO;

    // Cuánto de studentAmount ya se liberó al estudiante
    @Column(name = "released_amount", nullable = false)
    private BigDecimal releasedAmount = BigDecimal.ZERO;

    // Cuánto se le devolvió al cliente (con su parte de comisión)
    @Column(name = "refunded_amount", nullable = false)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    // Comprobante de Stripe (pi_...) cuando se paga
    private String stripeReference;

    private LocalDateTime paidAt;

    // Pago 2: fecha límite para pagarlo. Se fija cuando el cliente aprueba el Diseño; si llega sin pagar, el contrato
    // se cierra solo (SecondPaymentService). null mientras no corre el plazo.
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    // Recordatorios por correo ya enviados para este pago (1 al aprobar el Diseño, 2 a mitad del plazo, 3 un día antes)
    @Column(name = "reminders_sent")
    private Integer remindersSent = 0;

    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    public boolean isPaid() {
        return status != InstallmentStatus.PENDING;
    }

    // Lo que queda retenido de la parte del estudiante
    public BigDecimal heldAmount() {
        return status == InstallmentStatus.HELD ? studentAmount.subtract(releasedAmount) : BigDecimal.ZERO;
    }
}
