package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Recordatorio al cliente para que haga el pago 2. reminderNumber: 1 al aprobar el Diseño, 2 a mitad del plazo,
// 3 un día antes de la fecha límite.
@Getter
public class SecondPaymentReminderEvent extends ApplicationEvent {
    private final Long contractId;
    private final int reminderNumber;
    private final BigDecimal amount;
    private final LocalDateTime dueAt;

    public SecondPaymentReminderEvent(Object source, Long contractId, int reminderNumber, BigDecimal amount, LocalDateTime dueAt) {
        super(source);
        this.contractId = contractId;
        this.reminderNumber = reminderNumber;
        this.amount = amount;
        this.dueAt = dueAt;
    }
}
