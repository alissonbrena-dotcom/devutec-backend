package pe.edu.utec.devutec.payments;

// Conversación con Stripe. Es una interfaz para poder probar el servicio sin llamar a Stripe de verdad.
public interface StripeGateway {

    // Crea la página de pago de Stripe y devuelve su id y su link
    StripeCheckoutSession createCheckoutSession(CheckoutRequest request);

    // Pregunta a Stripe cómo va una sesión (si ya se pagó)
    StripeCheckoutSession getCheckoutSession(String sessionId);

    // Devuelve al cliente todo lo que pagó con ese PaymentIntent (pi_...)
    void refund(String paymentIntentId);

    // Devuelve solo una parte de lo que se pagó con ese PaymentIntent (en céntimos)
    void refund(String paymentIntentId, long amountInCents);

    record CheckoutRequest(
            String contractId,
            String productName,
            long amountInCents,
            String currency,
            String successUrl,
            String cancelUrl
    ) {
    }
}
