package pe.edu.utec.devutec.events;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.service.EmailService;

@Component
@RequiredArgsConstructor
public class PaymentReleasedNotifier {

    private final EmailService emailService;

    @EventListener
    @Async
    public void onPaymentReleased(PaymentReleasedEvent event) {
        String to = "freelancer@example.com"; // temporal, luego se reemplaza por el correo real del freelancer
        String subject = "¡Tu pago ha sido liberado!";
        String body = "Hola, el pago #" + event.getPayment().getId()
                + " de S/ " + event.getPayment().getAmount()
                + " ha sido liberado a tu cuenta. ¡Gracias por tu trabajo!";

        emailService.sendEmail(to, subject, body);
    }
}
