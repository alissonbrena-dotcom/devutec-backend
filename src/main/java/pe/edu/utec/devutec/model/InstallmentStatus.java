package pe.edu.utec.devutec.model;

// Estado de cada cuota del contrato (el pago en dos partes)
public enum InstallmentStatus {
    PENDING,   // Todavía no se paga
    HELD,      // Pagada y retenida en garantía
    RELEASED,  // Liberada completa al estudiante
    REFUNDED   // Lo no liberado se devolvió al cliente
}
