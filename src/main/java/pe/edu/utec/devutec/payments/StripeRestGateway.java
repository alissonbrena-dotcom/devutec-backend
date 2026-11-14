package pe.edu.utec.devutec.payments;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pe.edu.utec.devutec.exceptions.ApiException;

import java.util.Map;

// Llama a la API REST de Stripe directamente (https://docs.stripe.com/api/checkout/sessions).
// La clave secreta solo vive en el servidor, en la variable STRIPE_SECRET_KEY; nunca va al frontend.
@Slf4j
@Component
public class StripeRestGateway implements StripeGateway {

    private static final String STRIPE_API = "https://api.stripe.com/v1";

    private final String secretKey;
    private final RestClient restClient;

    public StripeRestGateway(@Value("${stripe.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
        this.restClient = RestClient.builder().baseUrl(STRIPE_API).build();
    }

    @Override
    public StripeCheckoutSession createCheckoutSession(CheckoutRequest request) {
        // Stripe recibe los datos como formulario, no como JSON
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("client_reference_id", request.contractId());
        form.add("metadata[contractId]", request.contractId());
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", request.currency());
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(request.amountInCents()));
        form.add("line_items[0][price_data][product_data][name]", request.productName());
        form.add("success_url", request.successUrl());
        form.add("cancel_url", request.cancelUrl());

        return call(() -> toSession(restClient.post()
                .uri("/checkout/sessions")
                .header("Authorization", "Bearer " + requireKey())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class)));
    }

    @Override
    public StripeCheckoutSession getCheckoutSession(String sessionId) {
        return call(() -> toSession(restClient.get()
                .uri("/checkout/sessions/{id}", sessionId)
                .header("Authorization", "Bearer " + requireKey())
                .retrieve()
                .body(Map.class)));
    }

    private String requireKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Los pagos con tarjeta no están configurados en el servidor");
        }
        return secretKey;
    }

    private StripeCheckoutSession call(java.util.function.Supplier<StripeCheckoutSession> request) {
        try {
            return request.get();
        } catch (RestClientException ex) {
            log.warn("Stripe respondió con un error: {}", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo completar la operación con Stripe. Intenta de nuevo.");
        }
    }

    private static StripeCheckoutSession toSession(Map<?, ?> body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe no devolvió una respuesta válida");
        }
        return new StripeCheckoutSession(
                text(body.get("id")),
                text(body.get("url")),
                text(body.get("payment_status")),
                text(body.get("payment_intent")),
                text(body.get("client_reference_id"))
        );
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
