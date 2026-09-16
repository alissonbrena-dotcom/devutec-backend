package pe.edu.utec.devutec.events;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PaymentReleasedNotifier {

    @EventListener
    @Async
    public void onPaymentReleased(PaymentReleasedEvent event) {
        System.out.println("Enviando correo: el pago #" + event.getPayment().getId()
                + " de S/ " + event.getPayment().getAmount() + " ha sido liberado al freelancer.");
    }
}
