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

    @Async
    @TransactionalEventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        emailService.sendHtmlEmail(
                event.getEmail(),
                "¡Bienvenido a DevUTEC!",
                "welcome",
                Map.of(
                        "name", event.getNombre(),
                        "isFreelancer", "FREELANCER".equals(event.getRol())
                )
        );
    }
}