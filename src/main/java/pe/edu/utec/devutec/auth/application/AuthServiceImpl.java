package pe.edu.utec.devutec.auth.application;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.dto.AuthResponse;
import pe.edu.utec.devutec.auth.dto.LoginRequest;
import pe.edu.utec.devutec.auth.dto.RefreshTokenRequest;
import pe.edu.utec.devutec.auth.dto.RegisterRequest;
import pe.edu.utec.devutec.auth.infrastructure.JwtService;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.exceptions.DuplicateResourceException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.UnauthorizedException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        ensureEmailIsAvailable(request.getEmail());
        ensureRoleCanRegister(request.getRol());

        User user = new User();
        user.setNombre(request.getNombre());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRol(request.getRol());
        User saved = userRepository.save(user);

        if (saved.getRol() == Role.FREELANCER) {
            createFreelancerProfile(saved);
        }
        return buildResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Email o contraseña incorrectos"));
        return buildResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        User user = findUserFromRefreshToken(refreshToken);
        if (!jwtService.isTokenValid(refreshToken, user, JwtService.REFRESH_TOKEN)) {
            throw new UnauthorizedException("Refresh token inválido o expirado");
        }
        return buildResponse(user);
    }

    private User findUserFromRefreshToken(String refreshToken) {
        try {
            String email = jwtService.extractEmail(refreshToken);
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("Refresh token inválido o expirado"));
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Refresh token inválido o expirado");
        }
    }

    private void ensureEmailIsAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("El email ya está registrado");
        }
    }

    private void ensureRoleCanRegister(Role role) {
        if (role == Role.ADMIN) {
            throw new ForbiddenOperationException("No se puede registrar un usuario con rol ADMIN");
        }
    }

    private void createFreelancerProfile(User user) {
        FreelancerProfile profile = new FreelancerProfile();
        profile.setUser(user);
        freelancerProfileRepository.save(profile);
    }

    private AuthResponse buildResponse(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                user.getEmail(),
                user.getRol().name()
        );
    }
}
