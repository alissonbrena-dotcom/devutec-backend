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
    private final EmailLinks links;

    @Async
    @TransactionalEventListener
    public void onContractDelivered(ContractDeliveredEvent event) {
        userRepository.findById(event.getClientId()).ifPresent(client ->
                emailService.sendHtmlEmail(
                        client.getEmail(),
                        event.getFreelancerName() + " entregó «" + event.getProjectTitle() + "»",
                        "contract-delivered",
                        Map.of(
                                "clientName", client.getNombre(),
                                "freelancerName", event.getFreelancerName(),
                                "projectTitle", event.getProjectTitle(),
                                "appUrl", links.base(),
                                "actionUrl", links.to("/contracts/" + event.getContractId()),
                                "deliveryUrl", event.getDeliveryUrl(),
                                // Map.of no acepta null: sin nota va vacío y la plantilla no la muestra
                                "deliveryNote", event.getDeliveryNote() != null ? event.getDeliveryNote() : ""
                        )
                )
        );
    }

    @Async
    @TransactionalEventListener
    public void onContractCreated(ContractCreatedEvent event) {
        userRepository.findById(event.getClientId()).ifPresent(client ->
                emailService.sendHtmlEmail(
                        client.getEmail(),
                        "Tu contrato con " + event.getFreelancerName() + " está en marcha",
                        "contract-created",
                        Map.of(
                                "clientName", client.getNombre(),
                                "freelancerName", event.getFreelancerName(),
                                "projectTitle", event.getProjectTitle(),
                                "amount", event.getAmount(),
                                "appUrl", links.base(),
                                "actionUrl", links.to("/contracts/" + event.getContractId())
                        )
                )
        );
    }
}