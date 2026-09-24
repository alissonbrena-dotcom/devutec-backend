package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ContractDeliveredEvent extends ApplicationEvent {

    private final Long clientId;
    private final String projectTitle;
    private final String freelancerName;

    public ContractDeliveredEvent(Object source, Long clientId, String projectTitle, String freelancerName) {
        super(source);
        this.clientId = clientId;
        this.projectTitle = projectTitle;
        this.freelancerName = freelancerName;
    }
}