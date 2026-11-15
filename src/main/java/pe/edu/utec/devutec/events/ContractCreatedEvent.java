package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

// Se publica cuando se crea un contrato (el cliente aceptó una postulación y su pago quedó en garantía).
@Getter
public class ContractCreatedEvent extends ApplicationEvent {
    private final Long clientId;
    private final String freelancerName;
    private final String projectTitle;
    private final BigDecimal amount;
    // Para el link "Ver el contrato" del correo
    private final Long contractId;

    public ContractCreatedEvent(Object source, Long clientId, String freelancerName, String projectTitle, BigDecimal amount, Long contractId) {
        super(source);
        this.clientId = clientId;
        this.freelancerName = freelancerName;
        this.projectTitle = projectTitle;
        this.amount = amount;
        this.contractId = contractId;
    }
}