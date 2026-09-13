package pe.edu.utec.devutec.payment.domain;

public enum PaymentStatus {
    HELD,       // Retenido en garantía (escrow)
    RELEASED,   // Liberado al freelancer
    REFUNDED    // Reembolsado al cliente
}
