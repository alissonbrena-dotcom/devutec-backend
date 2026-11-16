package pe.edu.utec.devutec.events;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.utec.devutec.service.EmailService;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserNotifier {

    private final EmailService emailService;
    private final EmailLinks links;

    @Async
    @TransactionalEventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        boolean isFreelancer = "FREELANCER".equals(event.getRol());
        emailService.sendHtmlEmail(
                event.getEmail(),
                "Bienvenido a DEVUTEC, " + event.getNombre(),
                "welcome",
                Map.of(
                        "name", event.getNombre(),
                        "isFreelancer", isFreelancer,
                        "appUrl", links.base(),
                        // El freelancer empieza completando su perfil; el cliente, publicando su proyecto
                        "actionUrl", links.to(isFreelancer ? "/profile/edit" : "/projects/new")
                )
        );
    }
}