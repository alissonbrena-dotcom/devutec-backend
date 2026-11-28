package pe.edu.utec.devutec.payments;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.utec.devutec.dto.CheckoutStartRequestDTO;

import static org.junit.jupiter.api.Assertions.*;

// Solo se aceptan links que abren la app (devutec:// en el APK, exp:// o exps:// en Expo Go).
// Así nadie puede usar la página /pago-listo para mandar a la gente a otra web.
class CheckoutStartRequestDTOTest {

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

    @ParameterizedTest
    @ValueSource(strings = {"devutec://contracts/8", "exp://172.20.10.2:8081/--/contracts/8", "exps://u.expo.dev/--/contracts/8"})
    void linksDeLaApp_sonValidos(String link) {
        assertTrue(validator.validate(new CheckoutStartRequestDTO(link, null)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://sitio-malo.com", "javascript:alert(1)", "devutec://con espacio", "devutec://"})
    void otrosLinks_noSonValidos(String link) {
        assertFalse(validator.validate(new CheckoutStartRequestDTO(link, null)).isEmpty());
    }

    @org.junit.jupiter.api.Test
    void sinLink_esValido() {
        assertTrue(validator.validate(new CheckoutStartRequestDTO(null, null)).isEmpty());
    }
}
