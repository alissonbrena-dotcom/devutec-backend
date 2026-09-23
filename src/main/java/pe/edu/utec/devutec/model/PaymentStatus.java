package pe.edu.utec.devutec.model;

public enum PaymentStatus {
    HELD,       // Retenido en garantía (escrow)
    RELEASED,   // Liberado al freelancer
    REFUNDED    // Reembolsado al cliente
}
