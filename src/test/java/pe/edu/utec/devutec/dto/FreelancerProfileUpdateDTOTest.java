package pe.edu.utec.devutec.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FreelancerProfileUpdateDTOTest {

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

    // TEST 1 - Un DTO con todos los datos válidos no tiene errores
    @Test
    void dto_conDatosValidos_noDeberiaTenerErrores() {
        // Given
        FreelancerProfileUpdateDTO dto = new FreelancerProfileUpdateDTO(
                "UTEC", 6, false, new BigDecimal("35.50"), "https://camila.dev", "camila-rios");

        // When + Then
        assertTrue(validator.validate(dto).isEmpty());
    }

    // TEST 2 - Un DTO vacío es válido porque todos los campos son opcionales
    @Test
    void dto_vacio_noDeberiaTenerErrores() {
        assertTrue(validator.validate(new FreelancerProfileUpdateDTO()).isEmpty());
    }

    // TEST 3 - El ciclo debe estar entre 1 y 10
    @Test
    void ciclo_fueraDeRango_deberiaSerInvalido() {
        assertHasErrorOn("ciclo", withCiclo(0));
        assertHasErrorOn("ciclo", withCiclo(11));
        assertTrue(validator.validate(withCiclo(1)).isEmpty());
        assertTrue(validator.validate(withCiclo(10)).isEmpty());
    }

    // TEST 4 - La tarifa por hora debe ser mayor a cero
    @Test
    void tarifaHora_ceroONegativa_deberiaSerInvalida() {
        FreelancerProfileUpdateDTO dto = new FreelancerProfileUpdateDTO();
        dto.setTarifaHora(BigDecimal.ZERO);
        assertHasErrorOn("tarifaHora", dto);

        dto.setTarifaHora(new BigDecimal("-5"));
        assertHasErrorOn("tarifaHora", dto);
    }

    // TEST 5 - La URL del portafolio debe ser una URL válida
    @Test
    void portafolioUrl_invalida_deberiaSerInvalida() {
        FreelancerProfileUpdateDTO dto = new FreelancerProfileUpdateDTO();
        dto.setPortafolioUrl("no-es-una-url");

        assertHasErrorOn("portafolioUrl", dto);
    }

    // TEST 6 - El usuario de GitHub sigue las reglas de GitHub
    @Test
    void githubUsername_invalido_deberiaSerInvalido() {
        assertHasErrorOn("githubUsername", withGithub("-empieza-con-guion"));
        assertHasErrorOn("githubUsername", withGithub("doble--guion"));
        assertHasErrorOn("githubUsername", withGithub("con espacio"));
        assertHasErrorOn("githubUsername", withGithub("a".repeat(40)));
        assertTrue(validator.validate(withGithub("a".repeat(39))).isEmpty());
    }

    // TEST 7 - Un egresado no puede indicar ciclo
    @Test
    void egresadoConCiclo_deberiaSerInvalido() {
        FreelancerProfileUpdateDTO dto = withCiclo(5);
        dto.setEgresado(true);

        assertHasErrorOn("cicloValidoParaEgresado", dto);
    }

    // TEST 8 - Un egresado sin ciclo es válido
    @Test
    void egresadoSinCiclo_deberiaSerValido() {
        FreelancerProfileUpdateDTO dto = new FreelancerProfileUpdateDTO();
        dto.setEgresado(true);

        assertTrue(validator.validate(dto).isEmpty());
    }

    private FreelancerProfileUpdateDTO withCiclo(Integer ciclo) {
        FreelancerProfileUpdateDTO dto = new FreelancerProfileUpdateDTO();
        dto.setCiclo(ciclo);
        return dto;
    }

    private FreelancerProfileUpdateDTO withGithub(String githubUsername) {
        FreelancerProfileUpdateDTO dto = new FreelancerProfileUpdateDTO();
        dto.setGithubUsername(githubUsername);
        return dto;
    }

    private void assertHasErrorOn(String field, FreelancerProfileUpdateDTO dto) {
        Set<ConstraintViolation<FreelancerProfileUpdateDTO>> violations = validator.validate(dto);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)),
                "Se esperaba un error en " + field + " pero hubo: " + violations);
    }
}
