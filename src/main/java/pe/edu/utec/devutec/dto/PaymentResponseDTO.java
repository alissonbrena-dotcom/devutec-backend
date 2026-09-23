package pe.edu.utec.devutec.dto;

import lombok.Getter;
import lombok.Setter;
import pe.edu.utec.devutec.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class PaymentResponseDTO {
    private Long id;
    private BigDecimal amount;
    private PaymentStatus status;
    private LocalDateTime createdAt;
}
