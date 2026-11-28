package pe.edu.utec.devutec.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Mi número es 987654321",
            "llámame al 987 654 321",
            "+51 987-654-321",
            "escríbeme a sofia.dev@gmail.com",
            "sofia arroba gmail punto com",
            "Escríbeme al wsp para coordinar",
            "hablemos por WhatsApp mejor",
            "pásame tu número",
            "te paso mi correo",
            "lo vemos por fuera de la plataforma",
            "Agrégame en Telegram"
    })
    void detectaDatosDeContactoYPedidosDeSalir(String text) {
        assertTrue(ContactFilter.hasContact(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Quiero un botón de WhatsApp en la web",
            "La web debe tener la carta, pedidos y el link a Instagram",
            "El plazo es el 2026-12-18 y el presupuesto S/ 880",
            "Cambia el color del encabezado a rojo",
            "Ya subí la versión 2 con 3 páginas y 12 platos",
            "Atendemos de 9 a 18 horas"
    })
    void dejaPasarTextosNormalesDelProyecto(String text) {
        assertFalse(ContactFilter.hasContact(text), text);
    }
}
