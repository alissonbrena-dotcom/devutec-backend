package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
@Getter
public class ContractCreatedEvent extends ApplicationEvent {
    private final Long clientId;
    private final String freelancerName;
    private final String projectTitle;
    private final BigDecimal amount;

    public ContractCreatedEvent(Object source, Long clientId, String freelancerName, String projectTitle, BigDecimal amount) {
        super(source);
        this.clientId = clientId;
        this.freelancerName = freelancerName;
        this.projectTitle = projectTitle;
        this.amount = amount;
    }
}