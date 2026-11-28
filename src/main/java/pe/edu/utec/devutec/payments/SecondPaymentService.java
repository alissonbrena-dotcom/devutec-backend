package pe.edu.utec.devutec.payments;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.events.ContractClosedForNonPaymentEvent;
import pe.edu.utec.devutec.events.SecondPaymentReminderEvent;
import pe.edu.utec.devutec.model.InstallmentStatus;
import pe.edu.utec.devutec.model.PaymentInstallment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.model.contract.StageStatus;
import pe.edu.utec.devutec.repository.PaymentInstallmentRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Qué pasa si el cliente no hace el pago 2 (plan original, Fase 3):
// 1. Al aprobar el Diseño, el pago 2 queda "por pagar" con fecha límite (aprobación + plazo) y se le avisa al cliente.
// 2. Recordatorios por correo: a mitad del plazo y un día antes de la fecha límite (sin repetirse).
// 3. Si llega la fecha límite sin pagar, el contrato se cierra solo: lo aprobado y la bolsa de emergencia son del
//    estudiante, las etapas que faltan se cancelan y lo que quede retenido vuelve al cliente.
// El job (SecondPaymentJob) llama a process() para cada pago pendiente; process() es idempotente.
@Service
public class SecondPaymentService {

    public static final String CLOSED_FOR_NON_PAYMENT = "PAGO_2_NO_REALIZADO";

    public enum Outcome { NOTHING, REMINDER_SENT, CONTRACT_CLOSED }

    private final PaymentInstallmentRepository repository;
    private final InstallmentService installmentService;
    private final ApplicationEventPublisher publisher;
    private final Duration term;
    private final Clock clock;

    // term: si installments.second-payment-term tiene valor (por ejemplo PT2M para la demo) manda ese; si no, los días
    @Autowired
    public SecondPaymentService(PaymentInstallmentRepository repository, InstallmentService installmentService,
                                ApplicationEventPublisher publisher,
                                @Value("${installments.second-payment-days:7}") long days,
                                @Value("${installments.second-payment-term:}") String termOverride) {
        this(repository, installmentService, publisher,
                termOverride == null || termOverride.isBlank() ? Duration.ofDays(days) : DurationStyle.detectAndParse(termOverride.trim()),
                Clock.systemDefaultZone());
    }

    SecondPaymentService(PaymentInstallmentRepository repository, InstallmentService installmentService,
                         ApplicationEventPublisher publisher, Duration term, Clock clock) {
        this.repository = repository;
        this.installmentService = installmentService;
        this.publisher = publisher;
        this.term = term;
        this.clock = clock;
    }

    public Duration getTerm() {
        return term;
    }

    // Se llama al aprobar la última etapa del pago 1 (el Diseño): arranca el plazo del pago 2 y manda el primer aviso
    @Transactional
    public void open(Contract contract) {
        secondInstallment(contract.getId())
                .filter(installment -> installment.getStatus() == InstallmentStatus.PENDING && installment.getDueAt() == null)
                .ifPresent(installment -> {
                    LocalDateTime now = now();
                    installment.setDueAt(now.plus(term));
                    sendReminder(installment, 1, now);
                    repository.save(installment);
                });
    }

    // Pagos 2 con plazo corriendo y sin pagar (los que revisa el job)
    @Transactional(readOnly = true)
    public List<Long> pendingIds() {
        return repository.findByStatusAndDueAtIsNotNullAndContract_StatusIn(InstallmentStatus.PENDING,
                        List.of(ContractStatus.IN_PROGRESS, ContractStatus.DELIVERED)).stream()
                .map(PaymentInstallment::getId)
                .toList();
    }

    // Revisa un pago 2: manda el recordatorio que toque o, si venció, cierra el contrato. Llamarlo dos veces no
    // repite nada: los recordatorios se cuentan y un contrato cerrado ya no se procesa.
    @Transactional
    public Outcome process(Long installmentId) {
        PaymentInstallment installment = repository.findById(installmentId).orElse(null);
        if (installment == null || installment.getStatus() != InstallmentStatus.PENDING || installment.getDueAt() == null) {
            return Outcome.NOTHING;
        }
        Contract contract = installment.getContract();
        if (contract.getStatus() != ContractStatus.IN_PROGRESS && contract.getStatus() != ContractStatus.DELIVERED) {
            return Outcome.NOTHING;
        }
        LocalDateTime now = now();
        LocalDateTime dueAt = installment.getDueAt();
        if (!now.isBefore(dueAt)) {
            close(contract, now);
            return Outcome.CONTRACT_CLOSED;
        }
        int sent = installment.getRemindersSent() == null ? 0 : installment.getRemindersSent();
        // Último aviso: un día antes (o el último cuarto del plazo, si es más corto, como en la demo)
        Duration lastNotice = term.dividedBy(4).compareTo(Duration.ofDays(1)) < 0 ? term.dividedBy(4) : Duration.ofDays(1);
        if (sent < 3 && !now.isBefore(dueAt.minus(lastNotice))) {
            sendReminder(installment, 3, now);
            repository.save(installment);
            return Outcome.REMINDER_SENT;
        }
        if (sent < 2 && !now.isBefore(dueAt.minus(term.dividedBy(2)))) {
            sendReminder(installment, 2, now);
            repository.save(installment);
            return Outcome.REMINDER_SENT;
        }
        return Outcome.NOTHING;
    }

    // Cierre por falta del pago 2: lo aprobado ya está liberado; la bolsa va al estudiante y lo que quede retenido
    // vuelve al cliente (refundUnreleased con la bolsa para el estudiante). El proyecto no vuelve a recibir postulaciones.
    private void close(Contract contract, LocalDateTime now) {
        contract.getStages().stream()
                .filter(stage -> stage.getStatus() != StageStatus.APPROVED)
                .forEach(stage -> stage.setStatus(StageStatus.CANCELLED));

        List<PaymentInstallment> plan = installmentService.list(contract.getId());
        BigDecimal fund = plan.stream()
                .filter(installment -> installment.getStatus() == InstallmentStatus.HELD)
                .map(installment -> installment.getReserveAmount().min(installment.heldAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = installmentService.refundUnreleased(contract, true);
        BigDecimal studentReceived = installmentService.list(contract.getId()).stream()
                .map(PaymentInstallment::getReleasedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        contract.setStatus(ContractStatus.CANCELLED);
        contract.setClosedReason(CLOSED_FOR_NON_PAYMENT);
        contract.setClosedAt(now);
        // Lo que el cliente pagó ya está repartido (al estudiante y, si sobró, devuelto): el pago deja de estar retenido
        if (contract.getPayment() != null) {
            contract.getPayment().setStatus(PaymentStatus.RELEASED);
        }
        publisher.publishEvent(new ContractClosedForNonPaymentEvent(this, contract.getId(), studentReceived, fund, refunded));
    }

    private void sendReminder(PaymentInstallment installment, int number, LocalDateTime now) {
        installment.setRemindersSent(number);
        installment.setLastReminderAt(now);
        publisher.publishEvent(new SecondPaymentReminderEvent(this, installment.getContract().getId(), number,
                installment.getClientAmount(), installment.getDueAt()));
    }

    private Optional<PaymentInstallment> secondInstallment(Long contractId) {
        return installmentService.list(contractId).stream()
                .filter(installment -> installment.getNumber() == 2)
                .findFirst();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
