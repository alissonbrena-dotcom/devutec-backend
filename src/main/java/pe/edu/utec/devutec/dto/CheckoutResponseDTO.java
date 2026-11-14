package pe.edu.utec.devutec.dto;

// El frontend abre checkoutUrl y guarda sessionId para confirmar el pago al volver
public record CheckoutResponseDTO(String sessionId, String checkoutUrl) {
}
