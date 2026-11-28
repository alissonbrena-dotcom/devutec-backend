package pe.edu.utec.devutec.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pe.edu.utec.devutec.exceptions.ApiException;

import java.time.Duration;
import java.util.Map;

// Suscripción mensual a las métricas con Stripe Checkout (modo prueba), usando la misma STRIPE_SECRET_KEY de los pagos.
// El plan se arma en el mismo pedido (price_data con recurring), así que no hay que crear productos en el panel de Stripe.
@Slf4j
@Component
public class MetricsStripeGateway {

    private final String secretKey;
    private final RestClient restClient;

    public MetricsStripeGateway(@Value("${stripe.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().baseUrl("https://api.stripe.com/v1").requestFactory(factory).build();
    }

    public record SubscriptionSession(String id, String url, String status, String subscriptionId, String contractId, String plan) {
    }

    // trialEndEpochSeconds: hasta cuándo no se cobra (el final de los 3 meses gratis), o null para cobrar desde hoy
    public SubscriptionSession createCheckout(Long contractId, MetricsPlan plan, Long trialEndEpochSeconds,
                                              String successUrl, String cancelUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("client_reference_id", String.valueOf(contractId));
        form.add("metadata[contractId]", String.valueOf(contractId));
        form.add("metadata[plan]", plan.name());
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", "pen");
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(plan.monthlyPrice().movePointRight(2).longValueExact()));
        form.add("line_items[0][price_data][recurring][interval]", "month");
        form.add("line_items[0][price_data][product_data][name]", plan.productName());
        if (trialEndEpochSeconds != null) {
            form.add("subscription_data[trial_end]", String.valueOf(trialEndEpochSeconds));
        }
        form.add("success_url", successUrl);
        form.add("cancel_url", cancelUrl);
        return call(() -> toSession(restClient.post().uri("/checkout/sessions")
                .header("Authorization", "Bearer " + requireKey())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class)));
    }

    public SubscriptionSession getCheckout(String sessionId) {
        return call(() -> toSession(restClient.get().uri("/checkout/sessions/{id}", sessionId)
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

    private SubscriptionSession call(java.util.function.Supplier<SubscriptionSession> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            log.warn("Stripe respondió con un error en la suscripción de métricas: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo completar la suscripción con Stripe. Intenta de nuevo.");
        }
    }

    private static SubscriptionSession toSession(Map<?, ?> body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe no devolvió una respuesta válida");
        }
        Object metadata = body.get("metadata");
        String plan = metadata instanceof Map<?, ?> map && map.get("plan") != null ? map.get("plan").toString() : null;
        return new SubscriptionSession(text(body.get("id")), text(body.get("url")), text(body.get("status")),
                text(body.get("subscription")), text(body.get("client_reference_id")), plan);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
