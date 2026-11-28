package pe.edu.utec.devutec.metrics;

import java.util.Locale;

// Lo que cuenta DEVUTEC en la web (o la app o la automatización) del cliente. Nada identifica a quien visita.
// amplitudeName es el nombre del evento en Amplitude.
public enum MetricType {
    // Alguien abrió una página (el script lo manda solo)
    PAGEVIEW,
    // Tocó un botón o un link (el script lo manda solo)
    CLICK,
    // Contactó al negocio: WhatsApp, llamar, "cómo llegar" o un formulario (data-devutec="contacto")
    CONTACT,
    // Hizo un pedido (data-devutec="pedido")
    ORDER,
    // Hizo una reserva (data-devutec="reserva")
    RESERVATION,
    // Cuánto tardó la página en cargar, en milisegundos (el script lo manda solo)
    PERFORMANCE,
    // Un evento propio con nombre, para apps y automatizaciones (por ejemplo "reporte_generado")
    CUSTOM;

    public String amplitudeName() {
        return "devutec_" + name().toLowerCase(Locale.ROOT);
    }
}
