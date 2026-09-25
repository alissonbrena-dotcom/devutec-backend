package pe.edu.utec.devutec.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.JwtService;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "clave-de-prueba-con-mas-de-32-caracteres-123456");
        ReflectionTestUtils.setField(jwtService, "accessExpirationMs", 60_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMs", 120_000L);

        user = new User();
        user.setId(7L);
        user.setEmail("free@test.com");
        user.setRol(Role.FREELANCER);
    }

    @Test
    void accessToken_incluyeClaimsDeUsuarioYRol() {
        String token = jwtService.generateAccessToken(user);

        assertEquals("free@test.com", jwtService.extractEmail(token));
        Long userId = jwtService.extractClaim(token, claims -> claims.get("userId", Long.class));
        assertEquals(Long.valueOf(7L), userId);
        assertEquals("FREELANCER", jwtService.extractClaim(token, claims -> claims.get("role", String.class)));
        assertEquals(JwtService.ACCESS_TOKEN, jwtService.extractTokenType(token));
    }

    @Test
    void refreshToken_noEsValidoComoAccessToken() {
        String refreshToken = jwtService.generateRefreshToken(user);

        assertTrue(jwtService.isTokenValid(refreshToken, user, JwtService.REFRESH_TOKEN));
        assertFalse(jwtService.isTokenValid(refreshToken, user, JwtService.ACCESS_TOKEN));
    }
}
