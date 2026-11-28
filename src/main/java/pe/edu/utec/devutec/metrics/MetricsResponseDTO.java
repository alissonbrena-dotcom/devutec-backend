package pe.edu.utec.devutec.metrics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Panel de métricas del contrato: estado, membresía, números del último mes y cómo integrarlo
public record MetricsResponseDTO(
        // El cliente ya las activó con su consentimiento
        boolean enabled,
        // El cliente las pidió al crear el proyecto: es un requisito de la Entrega final
        boolean requested,
        // Quien mira es el cliente: puede activarlas, desactivarlas y elegir su plan
        boolean canManage,
        String consentText,
        // Integración: llave, si ya llegó algún dato y de qué dominio
        String publicKey,
        LocalDateTime consentedAt,
        boolean integrated,
        LocalDateTime integratedAt,
        // Último dato recibido, para mostrar "última visita hace 2 minutos"
        LocalDateTime lastEventAt,
        // Todo lo recibido hasta ahora vino de localhost o de un link de prueba
        boolean onlyTestData,
        String allowedOrigin,
        Access access,
        // null si el panel está bloqueado (terminó la prueba gratis y no hay plan)
        Summary summary,
        Integration integration
) {
    public record Access(
            MetricsPlan plan,
            // Los 3 meses gratis: null si todavía no empiezan (empiezan con el primer dato real)
            LocalDateTime trialEndsAt,
            boolean trialActive,
            boolean locked,
            BigDecimal basicPrice,
            BigDecimal proPrice
    ) {
    }

    public record Summary(
            long visits,
            long contacts,
            long orders,
            long reservations,
            long clicks,
            Integer averageLoadMs,
            // Hay datos de prueba sumados en estos números
            boolean includesTestData,
            List<Day> lastDays
    ) {
    }

    public record Day(LocalDate date, long visits, long contacts) {
    }

    public record Integration(String scriptUrl, String scriptTag, String collectUrl, String postExample, String aiPrompt) {
    }
}
