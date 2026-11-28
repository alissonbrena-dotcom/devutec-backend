package pe.edu.utec.devutec.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MetricsController {

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final MetricsService metricsService;

    // Público: lo llama la web del cliente (el script de DEVUTEC) o el código del estudiante con un POST.
    // Acepta el cuerpo como texto o JSON y siempre responde 204, para no darle pistas a quien quiera abusar.
    // Ejemplo: {"key":"dv_...","type":"CONTACT","path":"/contacto"}
    @PostMapping("/api/v1/collect")
    public ResponseEntity<Void> collect(@RequestBody(required = false) String body, HttpServletRequest request) {
        if (body != null && body.length() <= 2_000) {
            try {
                metricsService.collect(JSON.readValue(body, CollectRequestDTO.class), request.getHeader("Origin"), clientIp(request));
            } catch (JacksonException ignored) {
                // Un evento mal formado simplemente no se cuenta
            } catch (RuntimeException e) {
                // Por ejemplo, dos eventos del mismo día llegando a la vez: se pierde uno, pero la web del cliente no ve errores
                log.debug("No se pudo contar un evento de métricas: {}", e.getMessage());
            }
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/contracts/{id}/metrics")
    public ResponseEntity<MetricsResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(metricsService.get(id));
    }

    // Solo el cliente, marcando el consentimiento
    @PostMapping("/api/v1/contracts/{id}/metrics")
    public ResponseEntity<MetricsResponseDTO> enable(@PathVariable Long id, @Valid @RequestBody MetricsConsentDTO request) {
        return ResponseEntity.ok(metricsService.enable(id));
    }

    // Retira el consentimiento y borra la llave y los contadores del panel
    @DeleteMapping("/api/v1/contracts/{id}/metrics")
    public ResponseEntity<MetricsResponseDTO> disable(@PathVariable Long id) {
        return ResponseEntity.ok(metricsService.disable(id));
    }

    // Reporte Pro (sale de Amplitude)
    @GetMapping("/api/v1/contracts/{id}/metrics/advanced")
    public ResponseEntity<AdvancedReportDTO> advanced(@PathVariable Long id) {
        return ResponseEntity.ok(metricsService.advanced(id));
    }

    // Membresía: abre Stripe para suscribirse a Básico o Pro y, al volver, confirma la suscripción
    @PostMapping("/api/v1/contracts/{id}/metrics/subscription")
    public ResponseEntity<MetricsSubscriptionDTOs.StartResponse> subscribe(@PathVariable Long id,
                                                                           @Valid @RequestBody MetricsSubscriptionDTOs.StartRequest request) {
        return ResponseEntity.ok(metricsService.startSubscription(id, request.plan()));
    }

    @PostMapping("/api/v1/contracts/{id}/metrics/subscription/confirm")
    public ResponseEntity<MetricsResponseDTO> confirm(@PathVariable Long id,
                                                      @Valid @RequestBody MetricsSubscriptionDTOs.ConfirmRequest request) {
        return ResponseEntity.ok(metricsService.confirmSubscription(id, request.sessionId()));
    }

    // Caddy pasa la IP real en X-Forwarded-For. Solo se usa en memoria para el límite de eventos y nunca se guarda
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
