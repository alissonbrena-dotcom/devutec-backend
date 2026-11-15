package pe.edu.utec.devutec.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.auth.infrastructure.GoogleTokenVerifier;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.UnauthorizedException;

import static org.junit.jupiter.api.Assertions.*;

class GoogleTokenVerifierTest {

    @Test
    void verify_sinClientIdsConfigurados_respondeServicioNoDisponible() {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier("");

        ApiException error = assertThrows(ApiException.class, () -> verifier.verify("cualquier-token"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    @Test
    void verify_tokenMalFormado_rechazaComoNoAutorizado() {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier("123.apps.googleusercontent.com");

        assertThrows(UnauthorizedException.class, () -> verifier.verify("esto-no-es-un-token"));
    }
}
