package pe.edu.utec.devutec.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService service;

    // Público: public/metricas.js (en la web de DEVUTEC) manda aquí cada visita o evento, como texto para no
    // necesitar permiso previo de CORS. Siempre responde 204: la web del cliente no se entera si algo se descartó.
    @PostMapping(value = "/api/v1/collect", consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> collect(@RequestBody(required = false) String body) {
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
