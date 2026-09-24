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
public class ContractNotifier {

    private final EmailService emailService;
    private final UserRepository userRepository;

    @Async
    @TransactionalEventListener
    public void onContractDelivered(ContractDeliveredEvent event) {
        userRepository.findById(event.getClientId()).ifPresent(client ->
                emailService.sendHtmlEmail(
                        client.getEmail(),
                        "Tu proyecto fue entregado",
                        "contract-delivered",
                        Map.of(
                                "clientName", client.getNombre(),
                                "freelancerName", event.getFreelancerName(),
                                "projectTitle", event.getProjectTitle()
                        )
                )
        );
    }
}