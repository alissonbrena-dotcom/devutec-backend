package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class PaymentReleasedEvent extends ApplicationEvent {

    private final Long paymentId;
    private final BigDecimal amount;

    public PaymentReleasedEvent(Object source, Long paymentId, BigDecimal amount) {
        super(source);
        this.paymentId = paymentId;
        this.amount = amount;
    }
}
