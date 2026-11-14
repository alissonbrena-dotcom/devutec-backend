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

// Pago del contrato con tarjeta usando Stripe Checkout en modo prueba.
// 1. El cliente pide pagar: se crea la página de pago de Stripe y se devuelve su link.
// 2. Al volver de Stripe, se confirma preguntándole a Stripe si de verdad se pagó, y se guarda el comprobante.
// El pago sigue retenido (HELD) hasta que el cliente confirma la entrega, igual que antes.
@Service
@RequiredArgsConstructor
public class CheckoutService {

    static final String CURRENCY = "pen";

    private final ContractRepository contractRepository;
    private final CurrentUserService currentUserService;
    private final ContractMapper contractMapper;
    private final StripeGateway stripeGateway;

    @Value("${app.frontend-url:https://devutec.vercel.app}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public CheckoutResponseDTO startCheckout(Long contractId) {
        Contract contract = findContractOfCurrentClient(contractId);
        ensurePayable(contract);

        String contractPage = frontendUrl + "/contracts/" + contractId;
        StripeCheckoutSession session = stripeGateway.createCheckoutSession(new StripeGateway.CheckoutRequest(
                String.valueOf(contractId),
                "DevUTEC - " + contract.getApplication().getProject().getTitle(),
                toCents(contract.getPayment().getAmount()),
                CURRENCY,
                // Stripe reemplaza {CHECKOUT_SESSION_ID} por el id real de la sesión
                contractPage + "?pago=ok&session_id={CHECKOUT_SESSION_ID}",
                contractPage + "?pago=cancelado"
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
        if (contract.getStatus() != ContractStatus.IN_PROGRESS || payment.getStatus() != PaymentStatus.HELD) {
            throw new InvalidOperationException("Solo se puede pagar un contrato en curso con el pago retenido");
        }
    }

    // Stripe trabaja en céntimos: S/ 1200.50 -> 120050
    static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
