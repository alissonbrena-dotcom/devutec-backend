package pe.edu.utec.devutec.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class MetricsController {

    private static final MediaType JAVASCRIPT = new MediaType("application", "javascript", StandardCharsets.UTF_8);

    private final MetricsService service;

    // Público: el script que el estudiante pega en la web que entrega
    @GetMapping("/api/v1/metrics/sdk.js")
    public ResponseEntity<String> sdk() throws IOException {
        String script = new ClassPathResource("metrics/sdk.js").getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(JAVASCRIPT)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(script);
    }

    // Público: el script manda aquí cada visita o evento (como texto, para no necesitar permiso previo de CORS).
    // Siempre responde 204: la web del cliente no tiene por qué enterarse si algo se descartó.
    @PostMapping(value = "/api/v1/metrics/events", consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> event(@RequestBody(required = false) String body) {
        service.record(body);
        return ResponseEntity.noContent().build();
    }

    // Cliente y estudiante del contrato: el código para instalar y si ya llegan datos
    @GetMapping("/api/v1/contracts/{contractId}/metrics/setup")
    public ResponseEntity<MetricsSetupDTO> setup(@PathVariable Long contractId) {
        return ResponseEntity.ok(service.setup(contractId));
    }

    // Cliente y estudiante del contrato: el panel de los últimos días (7, 30 o 90)
    @GetMapping("/api/v1/contracts/{contractId}/metrics")
    public ResponseEntity<MetricsDashboardDTO> dashboard(@PathVariable Long contractId,
                                                         @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(service.dashboard(contractId, days));
    }
}
