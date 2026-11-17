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
    // Link al trabajo y nota del freelancer (la nota puede ser null)
    private final String deliveryUrl;
    private final String deliveryNote;

    public ContractDeliveredEvent(Object source, Long clientId, String projectTitle, String freelancerName, Long contractId,
                                  String deliveryUrl, String deliveryNote) {
        super(source);
        this.contractId = contractId;
        this.deliveryUrl = deliveryUrl;
        this.deliveryNote = deliveryNote;
        this.clientId = clientId;
        this.projectTitle = projectTitle;
        this.freelancerName = freelancerName;
    }
}