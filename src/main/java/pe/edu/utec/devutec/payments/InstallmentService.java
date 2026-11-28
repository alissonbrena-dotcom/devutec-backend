package pe.edu.utec.devutec.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.dto.InstallmentResponseDTO;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.model.InstallmentStatus;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentInstallment;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.model.contract.StageStatus;
import pe.edu.utec.devutec.repository.PaymentInstallmentRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Pago en dos partes con garantía.
// - El monto del contrato es lo que paga el cliente e incluye la comisión: el estudiante recibe total / (1 + comisión).
// - Pago 1 (50 %, al aceptar): cubre Descubrimiento y Diseño, y guarda una bolsa de emergencia (10 % de lo del estudiante).
// - Pago 2 (50 %, al aprobar el Diseño): cubre Desarrollo y Entrega.
// - Regla: el estudiante nunca trabaja una etapa sin pagar. Lo aprobado es del estudiante; lo no hecho vuelve al cliente.
// - La bolsa se libera al final o, si el cliente abandona después de aprobar trabajo, compensa al estudiante.
@Service
public class InstallmentService {

    // Parte del total que se cobra en el pago 1
    static final BigDecimal FIRST_SHARE = new BigDecimal("0.50");
    // Bolsa de emergencia: parte de lo del estudiante que se guarda hasta el final
    static final BigDecimal RESERVE_SHARE = new BigDecimal("0.10");
    // Con montos muy chicos no se parte en dos (Stripe cobra un mínimo por pago)
    static final BigDecimal MIN_TOTAL_TO_SPLIT = new BigDecimal("20.00");

    private final PaymentInstallmentRepository repository;
    private final StripeGateway stripeGateway;
    private final BigDecimal commissionRate;

    public InstallmentService(PaymentInstallmentRepository repository,
                              StripeGateway stripeGateway,
                              @Value("${app.commission-rate:0.10}") BigDecimal commissionRate) {
        this.repository = repository;
        this.stripeGateway = stripeGateway;
        this.commissionRate = commissionRate;
    }

    // Lo del estudiante y la comisión dentro de un monto que paga el cliente. S/ 880 -> S/ 800 + S/ 80
    public Split split(BigDecimal clientAmount) {
        BigDecimal student = clientAmount.divide(BigDecimal.ONE.add(commissionRate), 2, RoundingMode.HALF_UP);
        return new Split(student, clientAmount.subtract(student));
    }

    public record Split(BigDecimal student, BigDecimal commission) {
    }

    // Al crear el contrato se arma el plan de pagos
    @Transactional
    public List<PaymentInstallment> createPlan(Contract contract) {
        if (repository.existsByContract_Id(contract.getId())) {
            return list(contract.getId());
        }
        BigDecimal total = contract.getPayment().getAmount().setScale(2, RoundingMode.HALF_UP);
        List<PaymentInstallment> plan = new ArrayList<>();
        if (total.compareTo(MIN_TOTAL_TO_SPLIT) < 0) {
            plan.add(build(contract, 1, "Pago único", total, BigDecimal.ZERO));
        } else {
            BigDecimal first = total.multiply(FIRST_SHARE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal second = total.subtract(first);
            BigDecimal reserve = split(total).student().multiply(RESERVE_SHARE).setScale(2, RoundingMode.HALF_UP);
            PaymentInstallment firstPayment = build(contract, 1, "Pago 1: al aceptar (Descubrimiento, Diseño y bolsa de emergencia)", first, reserve);
            PaymentInstallment secondPayment = build(contract, 2, "Pago 2: al aprobar el Diseño (Desarrollo y Entrega)", second, BigDecimal.ZERO);
            // La parte del estudiante del pago 2 es "total del estudiante menos la del pago 1", para que las dos sumen
            // exactamente total ÷ 1,10 (si cada una se redondea por separado, a veces se pierde un céntimo)
            BigDecimal studentTotal = split(total).student();
            secondPayment.setStudentAmount(studentTotal.subtract(firstPayment.getStudentAmount()));
            secondPayment.setCommissionAmount(second.subtract(secondPayment.getStudentAmount()));
            plan.add(firstPayment);
            plan.add(secondPayment);
        }
        return repository.saveAll(plan);
    }

    @Transactional(readOnly = true)
    public List<PaymentInstallment> list(Long contractId) {
        return repository.findByContract_IdOrderByNumberAsc(contractId);
    }

    // Los contratos anteriores a esta función no tienen plan y siguen con un solo pago
    @Transactional(readOnly = true)
    public boolean hasPlan(Long contractId) {
        return repository.existsByContract_Id(contractId);
    }

    @Transactional(readOnly = true)
    public boolean allPaid(Long contractId) {
        List<PaymentInstallment> plan = list(contractId);
        return !plan.isEmpty() && plan.stream().allMatch(PaymentInstallment::isPaid);
    }

    // La cuota que el cliente quiere pagar. Sin número, la siguiente pendiente.
    @Transactional(readOnly = true)
    public PaymentInstallment payable(Contract contract, Integer number) {
        List<PaymentInstallment> plan = list(contract.getId());
        PaymentInstallment installment = number == null
                ? plan.stream().filter(i -> !i.isPaid()).findFirst()
                    .orElseThrow(() -> new InvalidOperationException("Este contrato ya está pagado completo"))
                : plan.stream().filter(i -> i.getNumber().equals(number)).findFirst()
                    .orElseThrow(() -> new InvalidOperationException("Ese pago no existe en este contrato"));
        if (installment.isPaid()) {
            throw new InvalidOperationException("Ese pago ya se hizo");
        }
        if (!isPayableNow(contract, installment, plan)) {
            throw new InvalidOperationException("Todavía no se puede hacer este pago: primero paga el anterior y aprueba el Diseño");
        }
        return installment;
    }

    @Transactional(readOnly = true)
    public PaymentInstallment find(Long contractId, int number) {
        return list(contractId).stream()
                .filter(i -> i.getNumber() == number)
                .findFirst()
                .orElseThrow(() -> new InvalidOperationException("Ese pago no existe en este contrato"));
    }

    // Stripe confirmó el pago: queda retenido en garantía
    @Transactional
    public PaymentInstallment markPaid(PaymentInstallment installment, String stripeReference) {
        installment.setStatus(InstallmentStatus.HELD);
        installment.setStripeReference(stripeReference);
        installment.setPaidAt(LocalDateTime.now());
        // Con el pago 1 el contrato ya tiene dinero en garantía: se guarda el comprobante también en el pago del contrato,
        // así lo que depende de "pagado con tarjeta" (por ejemplo, compartir los celulares) funciona igual
        Payment payment = installment.getContract().getPayment();
        if (payment != null && payment.getStripeReference() == null) {
            payment.setStripeReference(stripeReference);
        }
        return repository.save(installment);
    }

    // Se aprobó una etapa: se libera al estudiante la parte del proyecto que cubre (0.20 = 20 %).
    // La bolsa de emergencia no se toca aquí. Devuelve cuánto se liberó.
    @Transactional
    public BigDecimal releaseForStage(Contract contract, BigDecimal shareOfProject) {
        List<PaymentInstallment> plan = list(contract.getId());
        BigDecimal studentTotal = plan.stream().map(PaymentInstallment::getStudentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal toRelease = studentTotal.multiply(shareOfProject).setScale(2, RoundingMode.HALF_UP);
        BigDecimal released = BigDecimal.ZERO;
        for (PaymentInstallment installment : plan) {
            if (installment.getStatus() != InstallmentStatus.HELD || released.compareTo(toRelease) >= 0) {
                continue;
            }
            BigDecimal available = installment.getStudentAmount()
                    .subtract(installment.getReleasedAmount())
                    .subtract(installment.getReserveAmount())
                    .max(BigDecimal.ZERO);
            BigDecimal take = available.min(toRelease.subtract(released));
            if (take.signum() > 0) {
                installment.setReleasedAmount(installment.getReleasedAmount().add(take));
                released = released.add(take);
            }
        }
        repository.saveAll(plan);
        return released;
    }

    // El cliente confirmó la entrega final: se libera todo lo retenido, incluida la bolsa
    @Transactional
    public BigDecimal releaseAll(Contract contract) {
        List<PaymentInstallment> plan = list(contract.getId());
        BigDecimal released = BigDecimal.ZERO;
        for (PaymentInstallment installment : plan) {
            if (installment.getStatus() == InstallmentStatus.HELD) {
                released = released.add(installment.heldAmount());
                installment.setReleasedAmount(installment.getStudentAmount());
                installment.setStatus(InstallmentStatus.RELEASED);
            }
        }
        repository.saveAll(plan);
        return released;
    }

    // Se cancela el contrato: lo no liberado vuelve al cliente con su parte de comisión (reembolso parcial en Stripe).
    // Con reserveToStudent, la bolsa de emergencia compensa al estudiante (el cliente abandonó con trabajo aprobado).
    // Devuelve cuánto se le devolvió al cliente.
    @Transactional
    public BigDecimal refundUnreleased(Contract contract, boolean reserveToStudent) {
        List<PaymentInstallment> plan = list(contract.getId());
        BigDecimal refundedTotal = BigDecimal.ZERO;
        for (PaymentInstallment installment : plan) {
            if (installment.getStatus() != InstallmentStatus.HELD) {
                continue;
            }
            if (reserveToStudent && installment.getReserveAmount().signum() > 0) {
                BigDecimal give = installment.getReserveAmount().min(installment.heldAmount());
                installment.setReleasedAmount(installment.getReleasedAmount().add(give));
            }
            BigDecimal remaining = installment.heldAmount();
            if (remaining.signum() <= 0) {
                installment.setStatus(InstallmentStatus.RELEASED);
                continue;
            }
            // Lo que se devuelve incluye la comisión de esa parte: DEVUTEC solo cobra por lo liberado
            BigDecimal refund = remaining.multiply(installment.getClientAmount())
                    .divide(installment.getStudentAmount(), 2, RoundingMode.HALF_DOWN)
                    .min(installment.getClientAmount().subtract(installment.getRefundedAmount()));
            String reference = installment.getStripeReference();
            if (reference != null && reference.startsWith("pi_") && refund.signum() > 0) {
                stripeGateway.refund(reference, CheckoutService.toCents(refund));
            }
            installment.setRefundedAmount(installment.getRefundedAmount().add(refund));
            installment.setStatus(InstallmentStatus.REFUNDED);
            refundedTotal = refundedTotal.add(refund);
        }
        repository.saveAll(plan);
        return refundedTotal;
    }

    @Transactional(readOnly = true)
    public List<InstallmentResponseDTO> toResponse(Contract contract) {
        List<PaymentInstallment> plan = list(contract.getId());
        return plan.stream()
                .map(i -> new InstallmentResponseDTO(
                        i.getNumber(),
                        i.getLabel(),
                        i.getClientAmount(),
                        i.getStudentAmount(),
                        i.getCommissionAmount(),
                        i.getReserveAmount(),
                        i.getReleasedAmount(),
                        i.getRefundedAmount(),
                        i.getStatus().name(),
                        isPayableNow(contract, i, plan),
                        i.getPaidAt(),
                        i.getDueAt()))
                .toList();
    }

    boolean isPayableNow(Contract contract, PaymentInstallment installment, List<PaymentInstallment> plan) {
        if (installment.isPaid()) {
            return false;
        }
        ContractStatus status = contract.getStatus();
        if (status != ContractStatus.IN_PROGRESS && status != ContractStatus.DELIVERED) {
            return false;
        }
        boolean previousPaid = plan.stream()
                .filter(other -> other.getNumber() < installment.getNumber())
                .allMatch(PaymentInstallment::isPaid);
        return previousPaid && (installment.getNumber() == 1 || secondInstallmentUnlocked(contract));
    }

    // El pago 2 se habilita cuando el cliente aprueba todas las etapas del pago 1 (hasta el Diseño).
    // Un contrato sin etapas lo habilita apenas se paga el pago 1.
    protected boolean secondInstallmentUnlocked(Contract contract) {
        return contract.getStages().stream()
                .filter(stage -> stage.getPaymentPart() != null && stage.getPaymentPart() == 1)
                .allMatch(stage -> stage.getStatus() == StageStatus.APPROVED);
    }

    // ¿Ya se pagó la cuota que cubre esta parte del contrato? Con un solo pago, esa cuota cubre todo.
    @Transactional(readOnly = true)
    public boolean isPartPaid(Long contractId, int paymentPart) {
        List<PaymentInstallment> plan = list(contractId);
        if (plan.isEmpty()) {
            return true;
        }
        int number = Math.min(paymentPart, plan.size());
        return plan.stream().filter(i -> i.getNumber() == number).anyMatch(PaymentInstallment::isPaid);
    }

    private PaymentInstallment build(Contract contract, int number, String label,
                                     BigDecimal clientAmount, BigDecimal reserve) {
        Split split = split(clientAmount);
        PaymentInstallment installment = new PaymentInstallment();
        installment.setContract(contract);
        installment.setNumber(number);
        installment.setLabel(label);
        installment.setClientAmount(clientAmount);
        installment.setStudentAmount(split.student());
        installment.setCommissionAmount(split.commission());
        installment.setReserveAmount(reserve);
        return installment;
    }
}
