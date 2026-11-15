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
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
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
        Contract contract = findContractOfCurrentClient(contractId);
        ensurePayable(contract);

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

        StripeCheckoutSession session = stripeGateway.createCheckoutSession(new StripeGateway.CheckoutRequest(
                String.valueOf(contractId),
                "DEVUTEC - " + contract.getApplication().getProject().getTitle(),
                toCents(contract.getPayment().getAmount()),
                CURRENCY,
                successUrl,
                cancelUrl
        ));
        return new CheckoutResponseDTO(session.id(), session.url());
    }

    @Transactional
    public ContractResponseDTO confirmCheckout(Long contractId, String sessionId) {
        Contract contract = findContractOfCurrentClient(contractId);
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
        if (requirePayment && contract.getPayment().getStripeReference() == null) {
            throw new InvalidOperationException("El cliente todavía no pagó este contrato con tarjeta");
        }
    }

    // Al cancelar: si el contrato se pagó con tarjeta, se le pide a Stripe que devuelva el dinero.
    // Si Stripe falla, la cancelación también falla y el contrato queda como estaba.
    public void refundIfPaid(Contract contract) {
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
