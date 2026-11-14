package pe.edu.utec.devutec.exceptions;

import org.springframework.http.HttpStatus;

// La cuenta de Google es nueva en DEVUTEC: el front debe preguntar si es cliente o freelancer
// y volver a llamar a /auth/google con el rol
public class GoogleSignupRequiredException extends ApiException {
    public GoogleSignupRequiredException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Elige si eres cliente o freelancer para terminar tu registro");
    }
}
