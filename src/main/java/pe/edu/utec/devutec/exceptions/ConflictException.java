package pe.edu.utec.devutec.exceptions;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.NOT_FOUND,message);
    }
}
