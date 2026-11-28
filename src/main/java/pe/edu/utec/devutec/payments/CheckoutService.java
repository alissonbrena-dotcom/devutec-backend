package pe.edu.utec.devutec.payments;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.CheckoutResponseDTO;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.ContractMapper;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentInstallment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.model.contract.StageStatus;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// Pago del contrato con tarjeta usando Stripe Checkout en modo prueba.
// 1. El cliente pide pagar: se crea la página de pago de Stripe y se devuelve su link.
// 2. Al volver de Stripe, se confirma preguntándole a Stripe si de verdad se pagó, y se guarda el comprobante.
// El pago sigue retenido (HELD) hasta que el cliente confirma la entrega, igual que antes.
@Service
@RequiredArgsConstructor
public class CheckoutService {

    static final String CURRENCY = "pen";
    // Stripe no acepta cobros en soles por debajo de este monto
    static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("2.00");

    private final ContractRepository contractRepository;
    private final CurrentUserService currentUserService;
    private final ContractMapper contractMapper;
    private final StripeGateway stripeGateway;
    private final InstallmentService installmentService;

    @Value("${app.frontend-url:https://devutec.vercel.app}")
    private String frontendUrl;

    // Interruptor: con true, el cliente tiene que pagar con tarjeta antes de poder confirmar la entrega.
    // Queda apagado hasta que la web y la app tengan el botón de pagar (variable STRIPE_REQUIRE_PAYMENT).
    @Value("${stripe.require-payment:false}")
    private boolean requirePayment;

    // Pago desde la web: al terminar, Stripe vuelve al detalle del contrato
    @Transactional(readOnly = true)
    public CheckoutResponseDTO startCheckout(Long contractId) {
        return startCheckout(contractId, null);
    }

    // appReturnUrl llega solo desde la app. Stripe solo acepta links https, así que en ese caso vuelve a una
    // página pública de la web (/pago-listo, sin iniciar sesión) que tiene el botón para abrir la app.
    @Transactional(readOnly = true)
    public CheckoutResponseDTO startCheckout(Long contractId, String appReturnUrl) {
        return startCheckout(contractId, appReturnUrl, null);
    }

    // installmentNumber: qué cuota pagar (1 o 2). Sin número, la siguiente pendiente.
    @Transactional(readOnly = true)
    public CheckoutResponseDTO startCheckout(Long contractId, String appReturnUrl, Integer installmentNumber) {
        Contract contract = findContractOfCurrentClient(contractId);
        // Contratos con pago en dos partes: se cobra una cuota
        PaymentInstallment installment = null;
        if (installmentService.hasPlan(contractId)) {
            installment = installmentService.payable(contract, installmentNumber);
            ensureMinimum(installment.getClientAmount());
        } else {
            ensurePayable(contract);
        }

        String successUrl;
        String cancelUrl;
        if (appReturnUrl != null && !appReturnUrl.isBlank()) {
            String returnPage = frontendUrl + "/pago-listo?volver=" + URLEncoder.encode(appReturnUrl, StandardCharsets.UTF_8);
            successUrl = returnPage + "&estado=ok";
            cancelUrl = returnPage + "&estado=cancelado";
        } else {
            String contractPage = frontendUrl + "/contracts/" + contractId;
            // Stripe reemplaza {CHECKOUT_SESSION_ID} por el id real de la sesión
            successUrl = contractPage + "?pago=ok&session_id={CHECKOUT_SESSION_ID}";
            cancelUrl = contractPage + "?pago=cancelado";
        }

        String title = "DEVUTEC - " + contract.getApplication().getProject().getTitle();
        StripeCheckoutSession session = stripeGateway.createCheckoutSession(new StripeGateway.CheckoutRequest(
                installment == null ? String.valueOf(contractId) : reference(contractId, installment.getNumber()),
                installment == null ? title : title + " (" + installment.getLabel() + ")",
                toCents(installment == null ? contract.getPayment().getAmount() : installment.getClientAmount()),
                CURRENCY,
                successUrl,
                cancelUrl
        ));
        return new CheckoutResponseDTO(session.id(), session.url());
    }

    @Transactional
    public ContractResponseDTO confirmCheckout(Long contractId, String sessionId) {
        Contract contract = findContractOfCurrentClient(contractId);
        if (installmentService.hasPlan(contractId)) {
            return confirmInstallment(contract, sessionId);
        }
        Payment payment = contract.getPayment();

        // Si ya se confirmó antes (por ejemplo, al recargar la página), no se repite
        if (payment.getStripeReference() != null) {
            return contractMapper.toResponse(contract);
        }

        StripeCheckoutSession session = stripeGateway.getCheckoutSession(sessionId);
        if (!String.valueOf(contractId).equals(session.clientReferenceId())) {
            throw new InvalidOperationException("Ese pago de Stripe no corresponde a este contrato");
        }
        if (!session.isPaid()) {
            throw new InvalidOperationException("El pago todavía no se completó en Stripe");
        }

        payment.setStripeReference(session.paymentIntentId() != null ? session.paymentIntentId() : session.id());
        return contractMapper.toResponse(contract);
    }

    // La referencia de Stripe dice de qué contrato y qué cuota es: "12-2" = contrato 12, pago 2
    private ContractResponseDTO confirmInstallment(Contract contract, String sessionId) {
        StripeCheckoutSession session = stripeGateway.getCheckoutSession(sessionId);
        String reference = session.clientReferenceId();
        String prefix = contract.getId() + "-";
        if (reference == null || !reference.startsWith(prefix)) {
            throw new InvalidOperationException("Ese pago de Stripe no corresponde a este contrato");
        }
        int number;
        try {
            number = Integer.parseInt(reference.substring(prefix.length()));
        } catch (NumberFormatException ex) {
            throw new InvalidOperationException("Ese pago de Stripe no corresponde a este contrato");
        }
        PaymentInstallment installment = installmentService.find(contract.getId(), number);
        // Si ya se confirmó antes (por ejemplo, al recargar la página), no se repite
        if (installment.getStripeReference() != null) {
            return contractMapper.toResponse(contract);
        }
        if (!session.isPaid()) {
            throw new InvalidOperationException("El pago todavía no se completó en Stripe");
        }
        installmentService.markPaid(installment, session.paymentIntentId() != null ? session.paymentIntentId() : session.id());
        return contractMapper.toResponse(contract);
    }

    static String reference(Long contractId, int installmentNumber) {
        return contractId + "-" + installmentNumber;
    }

    private void ensureMinimum(BigDecimal amount) {
        if (amount.compareTo(MINIMUM_AMOUNT) < 0) {
            throw new InvalidOperationException("El monto mínimo para pagar con tarjeta es S/ " + MINIMUM_AMOUNT);
        }
    }

    private Contract findContractOfCurrentClient(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado con id: " + contractId));
        User user = currentUserService.getCurrentUser();
        if (!user.getId().equals(contract.getApplication().getProject().getClientId())) {
            throw new ForbiddenOperationException("Solo el cliente del proyecto puede pagar este contrato");
        }
        return contract;
    }

    private void ensurePayable(Contract contract) {
        Payment payment = contract.getPayment();
        if (payment.getStripeReference() != null) {
            throw new InvalidOperationException("Este contrato ya fue pagado con tarjeta");
        }
        // También se puede pagar si el freelancer ya entregó: así, con el interruptor prendido, el cliente
        // que todavía no pagó puede hacerlo y luego confirmar
        boolean openContract = contract.getStatus() == ContractStatus.IN_PROGRESS || contract.getStatus() == ContractStatus.DELIVERED;
        if (!openContract || payment.getStatus() != PaymentStatus.HELD) {
            throw new InvalidOperationException("Solo se puede pagar un contrato en curso o entregado, con el pago retenido");
        }
        if (payment.getAmount().compareTo(MINIMUM_AMOUNT) < 0) {
            throw new InvalidOperationException("El monto mínimo para pagar con tarjeta es S/ " + MINIMUM_AMOUNT);
        }
    }

    // Antes de liberar el pago al freelancer: si el interruptor está prendido, el contrato tiene que estar pagado
    public void ensurePaidBeforeRelease(Contract contract) {
        if (requirePayment && installmentService.hasPlan(contract.getId())) {
            if (!installmentService.allPaid(contract.getId())) {
                throw new InvalidOperationException("El cliente todavía no hizo todos los pagos de este contrato");
            }
            return;
        }
        if (requirePayment && contract.getPayment().getStripeReference() == null) {
            throw new InvalidOperationException("El cliente todavía no pagó este contrato con tarjeta");
        }
    }

    // Antes de entregar una etapa: con el interruptor prendido, la cuota que la cubre tiene que estar pagada.
    // Así el estudiante nunca trabaja una etapa sin pagar.
    public void ensurePartPaid(Contract contract, Integer paymentPart) {
        if (!requirePayment || paymentPart == null || !installmentService.hasPlan(contract.getId())) {
            return;
        }
        if (!installmentService.isPartPaid(contract.getId(), paymentPart)) {
            throw new InvalidOperationException("El cliente todavía no hizo el pago " + paymentPart
                    + ". Cuando pague, podrás entregar esta etapa.");
        }
    }

    // Al cancelar: si el contrato se pagó con tarjeta, se le pide a Stripe que devuelva el dinero.
    // Si Stripe falla, la cancelación también falla y el contrato queda como estaba.
    public void refundIfPaid(Contract contract) {
        // Pago en dos partes: se devuelve solo lo que no se liberó al estudiante
        if (installmentService.hasPlan(contract.getId())) {
            // Si el cliente cancela después de aprobar trabajo, la bolsa de emergencia compensa al estudiante
            boolean approvedWork = contract.getStages().stream()
                    .anyMatch(stage -> stage.getStatus() == StageStatus.APPROVED);
            installmentService.refundUnreleased(contract, approvedWork);
            return;
        }
        String reference = contract.getPayment().getStripeReference();
        if (reference != null && reference.startsWith("pi_")) {
            stripeGateway.refund(reference);
        }
    }

    // Stripe trabaja en céntimos: S/ 1200.50 -> 120050
    static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
