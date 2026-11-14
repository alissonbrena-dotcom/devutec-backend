package pe.edu.utec.devutec.auth.infrastructure;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.UnauthorizedException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

// Comprueba que el ID token lo firmó Google, que no venció y que se emitió para nuestra app.
// Las llaves públicas de Google se descargan una vez y la librería las guarda en caché.
@Component
public class GoogleTokenVerifier {

    private final List<String> clientIds;
    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.client-ids:}") String clientIds) {
        this.clientIds = Arrays.stream(clientIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(this.clientIds)
                .build();
    }

    public GoogleAccount verify(String idToken) {
        if (clientIds.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El inicio de sesión con Google no está configurado");
        }
        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            token = null;
        }
        if (token == null) {
            throw new UnauthorizedException("No se pudo validar tu cuenta de Google. Intenta de nuevo");
        }
        GoogleIdToken.Payload payload = token.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new UnauthorizedException("Tu correo de Google no está verificado");
        }
        String nombre = (String) payload.get("name");
        String email = payload.getEmail().toLowerCase();
        return new GoogleAccount(email, nombre == null || nombre.isBlank() ? email.substring(0, email.indexOf('@')) : nombre);
    }
}
