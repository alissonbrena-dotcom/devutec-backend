package pe.edu.utec.devutec.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Arma los links de los correos hacia la web (por ejemplo https://devutec.vercel.app/contracts/42).
// La URL base sale de app.frontend-url (variable FRONTEND_URL) y también se usa para las imágenes del correo.
@Component
public class EmailLinks {

    private final String baseUrl;

    public EmailLinks(@Value("${app.frontend-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String base() {
        return baseUrl;
    }

    public String to(String path) {
        return baseUrl + path;
    }
}
