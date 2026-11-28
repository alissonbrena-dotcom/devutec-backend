package pe.edu.utec.devutec.payments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Cada cierto tiempo (installments.check-every, por defecto cada hora) revisa los pagos 2 pendientes: manda los
// recordatorios que toquen y cierra los contratos vencidos. Cada pago se procesa en su propia transacción, así un
// error en uno no frena a los demás. El admin lo puede correr en el momento (para la demo).
@Slf4j
@Component
@RequiredArgsConstructor
public class SecondPaymentJob {

    private final SecondPaymentService service;

    public record Summary(int reviewed, int remindersSent, int contractsClosed) {
    }

    @Scheduled(fixedDelayString = "${installments.check-every:PT1H}", initialDelayString = "${installments.check-initial-delay:PT1M}")
    public void scheduledRun() {
        Summary summary = runNow();
        if (summary.remindersSent() > 0 || summary.contractsClosed() > 0) {
            log.info("Pago 2: {} recordatorios enviados, {} contratos cerrados", summary.remindersSent(), summary.contractsClosed());
        }
    }

    public Summary runNow() {
        int reminders = 0;
        int closed = 0;
        var ids = service.pendingIds();
        for (Long id : ids) {
            try {
                switch (service.process(id)) {
                    case REMINDER_SENT -> reminders++;
                    case CONTRACT_CLOSED -> closed++;
                    default -> { }
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo revisar el pago 2 {}: {}", id, ex.getMessage());
            }
        }
        return new Summary(ids.size(), reminders, closed);
    }
}
