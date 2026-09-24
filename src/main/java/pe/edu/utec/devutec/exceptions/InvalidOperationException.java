package pe.edu.utec.devutec.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidOperationException extends ApiException {
    public InvalidOperationException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
