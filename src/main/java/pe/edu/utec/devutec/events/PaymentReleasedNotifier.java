package pe.edu.utec.devutec.events;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.EmailService;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentReleasedNotifier {

    private final EmailService emailService;
    private final ContractRepository contractRepository;

    @Async
    @TransactionalEventListener
    public void onPaymentReleased(PaymentReleasedEvent event) {
        contractRepository.findByPayment_Id(event.getPaymentId()).ifPresent(contract -> {
            Application application = contract.getApplication();
            User freelancer = application.getFreelancer().getUser();
            emailService.sendHtmlEmail(
                    freelancer.getEmail(),
                    "¡Tu pago ha sido liberado!",
                    "payment-released",
                    Map.of(
                            "freelancerName", freelancer.getNombre(),
                            "projectTitle", application.getProject().getTitle(),
                            "amount", event.getAmount(),
                            "paymentId", event.getPaymentId()
                    )
            );
        });
    }
}
