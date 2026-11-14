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
public class ApplicationNotifier {

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final EmailLinks links;

    @Async
    @TransactionalEventListener
    public void onApplicationCreated(ApplicationCreatedEvent event) {
        userRepository.findById(event.getClientId()).ifPresent(client ->
                emailService.sendHtmlEmail(
                        client.getEmail(),
                        event.getFreelancerName() + " postuló a «" + event.getProjectTitle() + "»",
                        "application-received",
                        Map.of(
                                "clientName", client.getNombre(),
                                "freelancerName", event.getFreelancerName(),
                                "projectTitle", event.getProjectTitle(),
                                "appUrl", links.base(),
                                "actionUrl", links.to("/projects/" + event.getProjectId() + "/applications")
                        )
                )
        );
    }

    @Async
    @TransactionalEventListener
    public void onApplicationAccepted(ApplicationAcceptedEvent event) {
        emailService.sendHtmlEmail(
                event.getFreelancerEmail(),
                "Te contrataron para «" + event.getProjectTitle() + "»",
                "application-accepted",
                Map.of(
                        "freelancerName", event.getFreelancerName(),
                        "projectTitle", event.getProjectTitle(),
                        "appUrl", links.base(),
                        "actionUrl", links.to("/contracts")
                )
        );
    }
}
