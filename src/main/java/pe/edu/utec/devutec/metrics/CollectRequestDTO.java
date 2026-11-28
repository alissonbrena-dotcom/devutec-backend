package pe.edu.utec.devutec.metrics;

// Lo que manda el script (o el código del estudiante) desde la web del cliente.
// Llega como texto (text/plain) para que el navegador lo envíe sin pedir permiso antes (sin preflight de CORS).
// - key: llave pública de la web · type: PAGEVIEW, CLICK, CONTACT, ORDER, RESERVATION, PERFORMANCE o CUSTOM
// - path: página (se le quita lo que va después de "?") · valueMs: milisegundos de carga (solo PERFORMANCE)
// - name: nombre del evento propio (solo CUSTOM, por ejemplo "reporte_generado")
// - visitorId: identificador al azar que dura solo esa visita, y solo si el visitante aceptó el aviso
// - source: de dónde llegó (instagram, facebook, google, tiktok, directo u otro) · device: celular o computadora
public record CollectRequestDTO(String key, String type, String path, Integer valueMs, String name,
                                String visitorId, String source, String device) {
}
