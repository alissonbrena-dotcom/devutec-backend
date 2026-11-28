package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

// El contrato se cerró porque el cliente no hizo el pago 2 a tiempo.
// studentReceived: todo lo que recibe el estudiante (etapas aprobadas + bolsa de emergencia).
// emergencyFund: la parte de eso que es la bolsa. refundedToClient: lo que se le devuelve al cliente.
@Getter
public class ContractClosedForNonPaymentEvent extends ApplicationEvent {
    private final Long contractId;
    private final BigDecimal studentReceived;
    private final BigDecimal emergencyFund;
    private final BigDecimal refundedToClient;

    public ContractClosedForNonPaymentEvent(Object source, Long contractId, BigDecimal studentReceived,
                                            BigDecimal emergencyFund, BigDecimal refundedToClient) {
        super(source);
        this.contractId = contractId;
        this.studentReceived = studentReceived;
        this.emergencyFund = emergencyFund;
        this.refundedToClient = refundedToClient;
    }
}
