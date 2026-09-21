package pe.edu.utec.devutec.events;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.service.EmailService;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentReleasedNotifier {

    private final EmailService emailService;

    @EventListener
    @Async
    public void onPaymentReleased(PaymentReleasedEvent event) {
        String to = "freelancer@example.com"; // temporal, luego se reemplaza por el correo real del freelancer

        Map<String, Object> variables = Map.of(
                "amount", event.getPayment().getAmount(),
                "paymentId", event.getPayment().getId()
        );

        emailService.sendHtmlEmail(
                to,
                "¡Tu pago ha sido liberado",
                "payment-released",
                variables
        );
    }
}
