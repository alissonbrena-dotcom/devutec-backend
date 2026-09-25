package pe.edu.utec.devutec.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.utec.devutec.auth.application.AuthServiceImpl;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.dto.RefreshTokenRequest;
import pe.edu.utec.devutec.auth.dto.RegisterRequest;
import pe.edu.utec.devutec.auth.infrastructure.JwtService;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.exceptions.DuplicateResourceException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.UnauthorizedException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private FreelancerProfileRepository freelancerProfileRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest request(Role role) {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Free Uno");
        request.setEmail("free@test.com");
        request.setPassword("password123");
        request.setRol(role);
        return request;
    }

    @Test
    void register_freelancer_creaSuPerfil() {
        when(userRepository.existsByEmail("free@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request(Role.FREELANCER));

        verify(freelancerProfileRepository, times(1)).save(any(FreelancerProfile.class));
    }

    @Test
    void register_cliente_noCreaPerfilDeFreelancer() {
        when(userRepository.existsByEmail("free@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request(Role.CLIENT));

        verify(freelancerProfileRepository, never()).save(any());
    }

    @Test
    void register_emailDuplicado_lanzaDuplicateResource() {
        when(userRepository.existsByEmail("free@test.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request(Role.CLIENT)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_admin_estaProhibido() {
        when(userRepository.existsByEmail("free@test.com")).thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () -> authService.register(request(Role.ADMIN)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void refresh_conTokenInvalido_lanzaUnauthorized() {
        User user = new User();
        user.setEmail("free@test.com");
        when(jwtService.extractEmail(anyString())).thenReturn("free@test.com");
        when(userRepository.findByEmail("free@test.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("token", user, JwtService.REFRESH_TOKEN)).thenReturn(false);

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken("token");

        assertThrows(UnauthorizedException.class, () -> authService.refresh(refreshRequest));
    }
}
