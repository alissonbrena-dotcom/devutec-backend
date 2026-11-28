package pe.edu.utec.devutec.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.utec.devutec.auth.application.AuthServiceImpl;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.dto.GoogleConfirmRequest;
import pe.edu.utec.devutec.auth.dto.GoogleLoginRequest;
import pe.edu.utec.devutec.auth.dto.RefreshTokenRequest;
import pe.edu.utec.devutec.auth.dto.RegisterRequest;
import pe.edu.utec.devutec.auth.infrastructure.GoogleAccount;
import pe.edu.utec.devutec.auth.infrastructure.GoogleTokenVerifier;
import pe.edu.utec.devutec.auth.infrastructure.JwtService;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.exceptions.DuplicateResourceException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.GoogleSignupRequiredException;
import pe.edu.utec.devutec.exceptions.UnauthorizedException;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CurrentUserService currentUserService;

    @Spy private pe.edu.utec.devutec.verification.StudentEmailPolicy studentEmailPolicy =
            new pe.edu.utec.devutec.verification.StudentEmailPolicy(true, ".edu.pe");

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest request(Role role) {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Free Uno");
        request.setEmail("free@utec.edu.pe");
        request.setPassword("Password123!");
        request.setRol(role);
        request.setAcceptedTerms(true);
        return request;
    }

    @Test
    void register_freelancer_guardaSuPerfilEnCascadaConElUsuario() {
        when(userRepository.existsByEmail("free@utec.edu.pe")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request(Role.FREELANCER));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User saved = captor.getValue();
        assertNotNull(saved.getFreelancerProfile());
        assertSame(saved, saved.getFreelancerProfile().getUser());
    }

    @Test
    void register_cliente_noCreaPerfilDeFreelancer() {
        when(userRepository.existsByEmail("free@utec.edu.pe")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request(Role.CLIENT));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertNull(captor.getValue().getFreelancerProfile());
    }

    @Test
    void register_emailDuplicado_lanzaDuplicateResource() {
        when(userRepository.existsByEmail("free@utec.edu.pe")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request(Role.CLIENT)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_admin_estaProhibido() {
        when(userRepository.existsByEmail("free@utec.edu.pe")).thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () -> authService.register(request(Role.ADMIN)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void refresh_conTokenInvalido_lanzaUnauthorized() {
        User user = new User();
        user.setEmail("free@utec.edu.pe");
        when(jwtService.extractEmail(anyString())).thenReturn("free@utec.edu.pe");
        when(userRepository.findByEmail("free@utec.edu.pe")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid("token", user, JwtService.REFRESH_TOKEN)).thenReturn(false);

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken("token");

        assertThrows(UnauthorizedException.class, () -> authService.refresh(refreshRequest));
    }

    private GoogleLoginRequest google(Role role) {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken("token-de-google");
        request.setRol(role);
        return request;
    }

    @Test
    void loginWithGoogle_cuentaExistente_entraSinCrearOtra() {
        User existing = new User();
        existing.setEmail("ana@gmail.com");
        existing.setRol(Role.CLIENT);
        when(googleTokenVerifier.verify("token-de-google")).thenReturn(new GoogleAccount("ana@gmail.com", "Ana"));
        when(userRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.of(existing));

        var response = authService.loginWithGoogle(google(null));

        assertEquals("CLIENT", response.getRol());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_cuentaNuevaSinRol_pideElegirRol() {
        when(googleTokenVerifier.verify("token-de-google")).thenReturn(new GoogleAccount("ana@gmail.com", "Ana"));
        when(userRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.empty());

        assertThrows(GoogleSignupRequiredException.class, () -> authService.loginWithGoogle(google(null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_cuentaNuevaFreelancer_creaUsuarioConPerfil() {
        when(googleTokenVerifier.verify("token-de-google")).thenReturn(new GoogleAccount("ana@pucp.edu.pe", "Ana"));
        when(userRepository.findByEmail("ana@pucp.edu.pe")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.loginWithGoogle(google(Role.FREELANCER));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("Ana", captor.getValue().getNombre());
        assertNotNull(captor.getValue().getFreelancerProfile());
        assertEquals("FREELANCER", response.getRol());
    }

    @Test
    void loginWithGoogle_cuentaNuevaAdmin_rechaza() {
        when(googleTokenVerifier.verify("token-de-google")).thenReturn(new GoogleAccount("ana@gmail.com", "Ana"));
        when(userRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.empty());

        assertThrows(ForbiddenOperationException.class, () -> authService.loginWithGoogle(google(Role.ADMIN)));
    }

    @Test
    void register_guardaLaFechaEnQueAceptoLosTerminos() {
        when(userRepository.existsByEmail("free@utec.edu.pe")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request(Role.CLIENT));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNotNull(captor.getValue().getTermsAcceptedAt());
    }

    @Test
    void loginWithGoogle_cuentaNueva_guardaLaFechaDeLosTerminos() {
        when(googleTokenVerifier.verify("token-de-google")).thenReturn(new GoogleAccount("ana@gmail.com", "Ana"));
        when(userRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.loginWithGoogle(google(Role.CLIENT));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNotNull(captor.getValue().getTermsAcceptedAt());
    }

    private User sesionDe(String email) {
        User user = new User();
        user.setEmail(email);
        when(currentUserService.getCurrentUser()).thenReturn(user);
        return user;
    }

    @Test
    void confirmWithGoogle_mismaCuenta_noLanzaError() {
        when(googleTokenVerifier.verify("token-de-google")).thenReturn(new GoogleAccount("Ana@Gmail.com", "Ana"));
        sesionDe("ana@gmail.com");

        assertDoesNotThrow(() -> authService.confirmWithGoogle(new GoogleConfirmRequest("token-de-google")));
    }

    @Test
    void confirmWithGoogle_otraCuenta_rechaza() {
        when(googleTokenVerifier.verify("token-de-google")).thenReturn(new GoogleAccount("otra@gmail.com", "Otra"));
        sesionDe("ana@gmail.com");

        assertThrows(ForbiddenOperationException.class,
                () -> authService.confirmWithGoogle(new GoogleConfirmRequest("token-de-google")));
    }

    @Test
    void confirmWithGoogle_tokenInvalido_noSigue() {
        when(googleTokenVerifier.verify("token-de-google")).thenThrow(new UnauthorizedException("No se pudo validar"));

        assertThrows(UnauthorizedException.class,
                () -> authService.confirmWithGoogle(new GoogleConfirmRequest("token-de-google")));
        verify(currentUserService, never()).getCurrentUser();
    }

    @Test
    void register_estudianteSinCorreoUniversitario_rechaza() {
        RegisterRequest request = request(Role.FREELANCER);
        request.setEmail("free@gmail.com");
        when(userRepository.existsByEmail("free@gmail.com")).thenReturn(false);

        var error = assertThrows(pe.edu.utec.devutec.exceptions.ApiException.class, () -> authService.register(request));
        assertTrue(error.getMessage().contains(".edu.pe"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_clienteConCualquierCorreo_sePermite() {
        RegisterRequest request = request(Role.CLIENT);
        request.setEmail("pizzeria@gmail.com");
        when(userRepository.existsByEmail("pizzeria@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> authService.register(request));
    }

    @Test
    void loginWithGoogle_estudianteNuevoConGmail_rechaza() {
        when(googleTokenVerifier.verify("token-de-google")).thenReturn(new GoogleAccount("ana@gmail.com", "Ana"));
        when(userRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.empty());

        assertThrows(pe.edu.utec.devutec.exceptions.ApiException.class,
                () -> authService.loginWithGoogle(google(Role.FREELANCER)));
        verify(userRepository, never()).save(any());
    }
}
