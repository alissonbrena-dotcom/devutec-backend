package pe.edu.utec.devutec.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Una cuota tal como la ve la web o la app
public record InstallmentResponseDTO(
        Integer number,
        String label,
        BigDecimal clientAmount,
        BigDecimal studentAmount,
        BigDecimal commissionAmount,
        BigDecimal reserveAmount,
        BigDecimal releasedAmount,
        BigDecimal refundedAmount,
        String status,
        boolean payableNow,
        LocalDateTime paidAt,
        // Fecha límite para pagar (solo el pago 2, desde que se aprueba el Diseño); null si no corre plazo
        LocalDateTime dueAt
) {
}
