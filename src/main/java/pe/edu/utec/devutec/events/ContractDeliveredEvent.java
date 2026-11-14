package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ContractDeliveredEvent extends ApplicationEvent {

    private final Long clientId;
    private final String projectTitle;
    private final String freelancerName;
    // Para el link "Revisar la entrega" del correo
    private final Long contractId;

    public ContractDeliveredEvent(Object source, Long clientId, String projectTitle, String freelancerName, Long contractId) {
        super(source);
        this.contractId = contractId;
        this.clientId = clientId;
        this.projectTitle = projectTitle;
        this.freelancerName = freelancerName;
    }
}