package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import pe.edu.utec.devutec.model.Payment;

@Getter
public class PaymentReleasedEvent extends ApplicationEvent {
    private final Payment payment;

    public PaymentReleasedEvent(Object source, Payment payment) {
        super(source);
        this.payment = payment;
    }
}
