package pe.edu.utec.devutec.payments;

// Lo que nos importa de una sesión de Stripe Checkout (la página de pago que arma Stripe)
public record StripeCheckoutSession(
        String id,
        String url,
        String paymentStatus,
        String paymentIntentId,
        String clientReferenceId
) {
    public boolean isPaid() {
        return "paid".equals(paymentStatus);
    }
}
