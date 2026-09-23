package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentRequestDTO {

    @NotNull(message= "El monto es obligatorio")
    @Positive(message= "El monto debe ser mayor a cero")
    private BigDecimal amount;
}
