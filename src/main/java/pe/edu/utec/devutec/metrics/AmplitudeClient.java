package pe.edu.utec.devutec.metrics;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// Conversación con Amplitude, el motor de analítica que DEVUTEC usa por detrás (recomendación de Aaron).
// - Enviar: cada evento que llega a /collect se reenvía con la API HTTP V2 (https://api2.amplitude.com/2/httpapi).
//   Lo manda nuestro servidor, así Amplitude nunca ve la IP del visitante ni pone cookies en su navegador.
// - Leer: el reporte Pro le pregunta a la Dashboard REST API (Event Segmentation), con la clave secreta solo en el servidor.
// Las claves viven en /etc/devutec.env (AMPLITUDE_API_KEY y AMPLITUDE_SECRET_KEY). Sin claves, no se envía nada
// y el reporte Pro dice que Amplitude no está configurado; el panel básico sigue funcionando.
@Slf4j
@Component
public class AmplitudeClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final String apiKey;
    private final String secretKey;
    private final String ingestUrl;
    private final String dashboardUrl;
    private final RestClient restClient;
    // Envío en segundo plano para que /collect responda al toque. Si se juntan demasiados eventos, se descartan
    // (es analítica: perder alguno es mejor que trabar el servidor)
    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2_000), new ThreadPoolExecutor.AbortPolicy());

    public AmplitudeClient(@Value("${amplitude.api-key:}") String apiKey,
                           @Value("${amplitude.secret-key:}") String secretKey,
                           @Value("${amplitude.region:us}") String region) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        boolean eu = "eu".equalsIgnoreCase(region);
        this.ingestUrl = eu ? "https://api.eu.amplitude.com/2/httpapi" : "https://api2.amplitude.com/2/httpapi";
        this.dashboardUrl = eu ? "https://analytics.eu.amplitude.com" : "https://amplitude.com";
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean canSend() {
        return !apiKey.isBlank();
    }

    public boolean canRead() {
        return !apiKey.isBlank() && !secretKey.isBlank();
    }

    // Un evento ya limpio (sin IP ni datos personales). deviceId es un identificador al azar que dura solo esa visita
    public record Event(String eventType, String deviceId, long timeMillis, String insertId, Map<String, Object> properties) {
    }

    public void send(Event event) {
        if (!canSend()) {
            return;
        }
        try {
            sender.execute(() -> post(event));
        } catch (RejectedExecutionException e) {
            log.warn("Demasiados eventos de métricas en cola: se descartó uno");
        }
    }

    private void post(Event event) {
        Map<String, Object> amplitudeEvent = new LinkedHashMap<>();
        amplitudeEvent.put("event_type", event.eventType());
        amplitudeEvent.put("device_id", event.deviceId());
        amplitudeEvent.put("time", event.timeMillis());
        amplitudeEvent.put("insert_id", event.insertId());
        amplitudeEvent.put("platform", "Web");
        amplitudeEvent.put("event_properties", event.properties());
        Map<String, Object> body = Map.of("api_key", apiKey, "events", List.of(amplitudeEvent));
        try {
            restClient.post().uri(ingestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JSON.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Amplitude no recibió un evento de métricas: {}", e.getMessage());
        }
    }

    // Resultado de una consulta: una serie por grupo (por ejemplo una por página), con su total en el rango y por día
    public record Segmentation(List<String> labels, List<Long> totals, List<String> days, List<List<Long>> perDay) {
        public long total() {
            return totals.stream().mapToLong(Long::longValue).sum();
        }
    }

    // Cuenta un evento de una web (filtrado por su llave), del día start al end. groupBy es una propiedad del evento
    // (por ejemplo "pagina" o "origen") o null para el total. interval: 1 = por día, 30 = por mes
    public Segmentation segmentation(String eventType, String siteKey, LocalDate start, LocalDate end, String groupBy, int interval) {
        Map<String, Object> filter = Map.of(
                "subprop_type", "event",
                "subprop_key", "web",
                "subprop_op", "is",
                "subprop_value", List.of(siteKey));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("event_type", "ce:" + eventType);
        e.put("filters", List.of(filter));
        if (groupBy != null) {
            e.put("group_by", List.of(Map.of("type", "event", "value", groupBy)));
        }
        String auth = Base64.getEncoder().encodeToString((apiKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        Map<?, ?> response = restClient.get()
                .uri(dashboardUrl + "/api/2/events/segmentation?e={e}&m=totals&start={start}&end={end}&i={i}&limit=20",
                        JSON.writeValueAsString(e), start.format(DAY), end.format(DAY), interval)
                .header("Authorization", "Basic " + auth)
                .retrieve()
                .body(Map.class);
        return parse(response);
    }

    @SuppressWarnings("unchecked")
    static Segmentation parse(Map<?, ?> response) {
        Map<String, Object> data = response == null ? Map.of() : (Map<String, Object>) response.get("data");
        if (data == null) {
            return new Segmentation(List.of(), List.of(), List.of(), List.of());
        }
        List<String> days = new ArrayList<>();
        for (Object day : (List<Object>) data.getOrDefault("xValues", List.of())) {
            days.add(String.valueOf(day));
        }
        List<String> labels = new ArrayList<>();
        for (Object label : (List<Object>) data.getOrDefault("seriesLabels", List.of())) {
            // Con group_by la etiqueta viene como [índice, "valor"]; sin group_by, como 0
            labels.add(label instanceof List<?> parts && parts.size() > 1 ? String.valueOf(parts.get(1)) : String.valueOf(label));
        }
        List<List<Long>> perDay = new ArrayList<>();
        for (Object series : (List<Object>) data.getOrDefault("series", List.of())) {
            List<Long> values = new ArrayList<>();
            for (Object value : (List<Object>) series) {
                values.add(value instanceof Number number ? number.longValue() : 0L);
            }
            perDay.add(values);
        }
        List<Long> totals = new ArrayList<>();
        for (List<Long> values : perDay) {
            totals.add(values.stream().mapToLong(Long::longValue).sum());
        }
        return new Segmentation(labels, totals, days, perDay);
    }

    @PreDestroy
    void stop() {
        sender.shutdown();
    }
}
