package pe.edu.utec.devutec.moderation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.support.ContactFilter;

// Aplica el filtro de contacto donde la gente escribe texto libre. Si bloquea, deja un reporte automático para el admin.
// - Antes del contrato (proyecto, postulación): ni datos de contacto ni intención de salirse.
// - En el contrato (entrega, pedido de cambios): solo la intención (sobre todo pagar por fuera). Los datos de contacto
//   se permiten porque el pago ya está en garantía y, por ejemplo, el número del negocio puede ir en la web.
@Component
@RequiredArgsConstructor
public class ContactGuard {

    private final ReportService reportService;

    public void checkBeforeContract(User author, String place, String... texts) {
        for (String text : texts) {
            if (ContactFilter.hasLeaveIntent(text)) block(author, place, text, ContactFilter.LEAVE_MESSAGE);
            if (ContactFilter.hasContactData(text)) block(author, place, text, ContactFilter.CONTACT_MESSAGE);
        }
    }

    public void checkInContract(User author, String place, String text) {
        if (ContactFilter.hasLeaveIntent(text)) block(author, place, text, ContactFilter.LEAVE_MESSAGE);
    }

    private void block(User author, String place, String text, String message) {
        reportService.recordBlocked(author, place, text);
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
