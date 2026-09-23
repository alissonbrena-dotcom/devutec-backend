package pe.edu.utec.devutec.service;

import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.UnauthorizedException;

@Repository
public class CurrenteUserService {
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || ! (authentication.getPrincipal() instanceof User user)) {
            throw new UnauthorizedException("Debes iniciar sesión para realizar esta acción");
        }
        return user;
    }
}
