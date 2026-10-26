package pe.edu.utec.devutec.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig(null, null);

    // TEST 1 - Con los valores por defecto se permiten los dos orígenes locales del frontend
    @Test
    void corsConfigurationSource_valoresPorDefecto_deberiaPermitirOrigenesLocales() {
        // When
        CorsConfiguration configuration = corsConfigurationFor("http://localhost:5173,http://localhost:3000");

        // Then
        assertEquals(List.of("http://localhost:5173", "http://localhost:3000"), configuration.getAllowedOrigins());
    }

    // TEST 2 - Se eliminan los espacios alrededor de cada origen y se descartan los valores vacíos
    @Test
    void corsConfigurationSource_conEspaciosYValoresVacios_deberiaLimpiarLaLista() {
        // When
        CorsConfiguration configuration = corsConfigurationFor(" https://devutec.vercel.app , ,http://localhost:5173 ,");

        // Then
        assertEquals(List.of("https://devutec.vercel.app", "http://localhost:5173"), configuration.getAllowedOrigins());
    }

    // TEST 3 - Un valor sin orígenes válidos no permite ningún origen
    @Test
    void corsConfigurationSource_sinOrigenesValidos_noDeberiaPermitirNinguno() {
        // When
        CorsConfiguration configuration = corsConfigurationFor(" , ");

        // Then
        assertTrue(configuration.getAllowedOrigins().isEmpty());
    }

    // TEST 4 - Se mantienen los métodos HTTP, los headers y las credenciales que usa el frontend
    @Test
    void corsConfigurationSource_deberiaMantenerMetodosHeadersYCredenciales() {
        // When
        CorsConfiguration configuration = corsConfigurationFor("https://devutec.vercel.app");

        // Then
        assertEquals(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"), configuration.getAllowedMethods());
        assertEquals(List.of("*"), configuration.getAllowedHeaders());
        assertTrue(configuration.getAllowCredentials());
    }

    private CorsConfiguration corsConfigurationFor(String allowedOrigins) {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) securityConfig.corsConfigurationSource(allowedOrigins);
        return source.getCorsConfigurations().get("/**");
    }
}
