package pe.edu.utec.devutec.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.UnauthorizedException;

@Service
public class CurrentUserService {

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new UnauthorizedException("Debes iniciar sesión para realizar esta acción");
        }
        return user;
    }
}