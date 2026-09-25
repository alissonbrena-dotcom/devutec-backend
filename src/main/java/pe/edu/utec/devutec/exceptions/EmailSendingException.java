package pe.edu.utec.devutec.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class EmailSendingException extends ApiException {
    private final String recipient;
    private final String templateName;

    public EmailSendingException(String recipient, String templateName, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar el correo '" + templateName + "' a " + recipient);
        this.recipient = recipient;
        this.templateName = templateName;
        initCause(cause);
    }
}
