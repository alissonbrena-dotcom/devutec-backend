package pe.edu.utec.devutec.events;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.service.EmailService;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApplicationNotifier{
    private final EmailService emailService;
    private final UserRepository userRepository;

    @Async
    @TransactionalEventListener
    public void onApplicationCreated(ApplicationCreatedEvent event) {
        userRepository.findById(event.getClientId()).ifPresent(client ->
                emailService.sendHtmlEmail(
                        client.getEmail(),
                        "Nueva postulación a tu proyecto",
                        "application-received",
                        Map.of(
                                "clientName", client.getNombre(),
                                "freelancerName", event.getFreelancerName(),
                                "projectTitle", event.getProjectTitle()
                        )
                )
        );
    }

    @Async
    @TransactionalEventListener
    public void onApplicationAccepted(ApplicationAcceptedEvent event) {
        emailService.sendHtmlEmail(
                event.getFreelancerEmail(),
                "¡Tu postulación fue aceptada!",
                "application-accepted",
                Map.of(
                        "freelancerName", event.getFreelancerName(),
                        "projectTitle", event.getProjectTitle()
                )
        );
    }
}
