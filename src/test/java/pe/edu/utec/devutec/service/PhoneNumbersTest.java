package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.exceptions.ApiException;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumbersTest {

    // TEST 1: se acepta como la gente lo escribe y se guardan solo los 9 dígitos
    @ParameterizedTest
    @ValueSource(strings = {"987654321", "987 654 321", "987-654-321", "+51 987 654 321", "+51987654321", "51987654321", "0051987654321", "(987) 654-321"})
    void normalize_formatosValidos_deberiaDejarLos9Digitos(String raw) {
        assertEquals("987654321", PhoneNumbers.normalize(raw));
    }

    // TEST 2: lo que no es un celular peruano se rechaza con 400
    @ParameterizedTest
    @ValueSource(strings = {"98765432", "9876543210", "887654321", "014567890", "+52 987654321", "98765432a", "nueve"})
    void normalize_formatosInvalidos_deberiaLanzar400(String raw) {
        ApiException ex = assertThrows(ApiException.class, () -> PhoneNumbers.normalize(raw));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // TEST 3: vacío o null también se rechaza
    @ParameterizedTest
    @NullAndEmptySource
    void normalize_vacio_deberiaLanzar400(String raw) {
        assertThrows(ApiException.class, () -> PhoneNumbers.normalize(raw));
    }

    // TEST 4: el mensaje explica el formato esperado
    @Test
    void normalize_invalido_deberiaExplicarElFormato() {
        ApiException ex = assertThrows(ApiException.class, () -> PhoneNumbers.normalize("123"));
        assertTrue(ex.getMessage().contains("9 dígitos"));
    }
}
