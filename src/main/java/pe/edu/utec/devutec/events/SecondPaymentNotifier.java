package pe.edu.utec.devutec.events;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.EmailService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// Correos del pago 2: los recordatorios al cliente y el aviso de cierre por falta de pago a los dos
@Component
@RequiredArgsConstructor
public class SecondPaymentNotifier {

    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, h:mm a", Locale.forLanguageTag("es-PE"));

    private final EmailService emailService;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final EmailLinks links;

    // Corre en otro hilo después de que se guarda el cambio: abre su propia transacción de lectura para poder cargar
    // el proyecto, el cliente y el estudiante del contrato
    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onReminder(SecondPaymentReminderEvent event) {
        contractRepository.findById(event.getContractId()).ifPresent(contract -> {
            Application application = contract.getApplication();
            userRepository.findById(application.getProject().getClientId()).ifPresent(client -> {
                String subject = switch (event.getReminderNumber()) {
                    case 1 -> "Ya puedes hacer el pago 2 de «" + application.getProject().getTitle() + "»";
                    case 2 -> "Recuerda hacer el pago 2 de «" + application.getProject().getTitle() + "»";
                    default -> "Último aviso: el pago 2 vence pronto";
                };
                Map<String, Object> vars = base(contract.getId());
                vars.put("clientName", client.getNombre());
                vars.put("freelancerName", application.getFreelancer().getUser().getNombre());
                vars.put("projectTitle", application.getProject().getTitle());
                vars.put("amount", money(event.getAmount()));
                vars.put("dueAt", event.getDueAt().format(DUE));
                vars.put("reminderNumber", event.getReminderNumber());
                emailService.sendHtmlEmail(client.getEmail(), subject, "second-payment-reminder", vars);
            });
        });
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onClosed(ContractClosedForNonPaymentEvent event) {
        contractRepository.findById(event.getContractId()).ifPresent(contract -> {
            Application application = contract.getApplication();
            User freelancer = application.getFreelancer().getUser();
            String title = application.getProject().getTitle();
            userRepository.findById(application.getProject().getClientId()).ifPresent(client -> {
                Map<String, Object> vars = base(contract.getId());
                vars.put("clientName", client.getNombre());
                vars.put("freelancerName", freelancer.getNombre());
                vars.put("projectTitle", title);
                vars.put("refunded", money(event.getRefundedToClient()));
                emailService.sendHtmlEmail(client.getEmail(), "Se cerró el contrato de «" + title + "»",
                        "contract-closed-client", vars);
            });
            Map<String, Object> vars = base(contract.getId());
            vars.put("freelancerName", freelancer.getNombre());
            vars.put("projectTitle", title);
            vars.put("received", money(event.getStudentReceived()));
            vars.put("fund", money(event.getEmergencyFund()));
            emailService.sendHtmlEmail(freelancer.getEmail(), "Recibes la bolsa de emergencia de «" + title + "»",
                    "contract-closed-student", vars);
        });
    }

    private Map<String, Object> base(Long contractId) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("appUrl", links.base());
        vars.put("actionUrl", links.to("/contracts/" + contractId));
        return vars;
    }

    private static String money(BigDecimal amount) {
        return "S/ " + (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
