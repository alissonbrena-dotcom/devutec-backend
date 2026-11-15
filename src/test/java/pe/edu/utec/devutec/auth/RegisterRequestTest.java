package pe.edu.utec.devutec.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.dto.RegisterRequest;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Reglas de la contraseña al registrarse: 8 a 100 caracteres, una mayúscula, una minúscula, un número y un símbolo
class RegisterRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private RegisterRequest request(String password) {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Café Aula 3");
        request.setEmail("cafe@devutec.pe");
        request.setPassword(password);
        request.setRol(Role.CLIENT);
        return request;
    }

    private Set<ConstraintViolation<RegisterRequest>> passwordErrors(String password) {
        Set<ConstraintViolation<RegisterRequest>> violations = new HashSet<>(validator.validate(request(password)));
        violations.removeIf(v -> !v.getPropertyPath().toString().equals("password"));
        return violations;
    }

    // TEST 1 - Contraseñas que cumplen todas las reglas
    @ParameterizedTest
    @ValueSource(strings = {"Cliente123!", "Freelancer123.", "Abcdef1@", "Mi-Clave-2026"})
    void password_fuerte_noDeberiaTenerErrores(String password) {
        assertTrue(passwordErrors(password).isEmpty(), password);
    }

    // TEST 2 - A cada una le falta algo: símbolo, mayúscula, minúscula, número o largo
    @ParameterizedTest
    @ValueSource(strings = {"Cliente123", "cliente123!", "CLIENTE123!", "Cliente!!!", "Cl1!", "12345678"})
    void password_debil_deberiaTenerError(String password) {
        assertFalse(passwordErrors(password).isEmpty(), password);
    }
}
