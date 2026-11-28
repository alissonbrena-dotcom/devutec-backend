package pe.edu.utec.devutec.metrics;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Una visita o un evento (por ejemplo «pedido») en la web del cliente. Es anónimo: no se guarda la IP ni datos
// personales; sessionId es un número al azar por pestaña, solo para contar visitantes distintos.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "metric_events", indexes = @Index(name = "idx_metric_events_site_date", columnList = "site_id, created_at"))
public class MetricEvent {

    public static final String VIEW = "view";
    public static final String EVENT = "event";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private TrackedSite site;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(length = 200)
    private String path;

    @Column(length = 60)
    private String label;

    @Column(name = "session_id", length = 40)
    private String sessionId;

    // Lo que tardó en cargar la página, en milisegundos (solo en las visitas)
    @Column(name = "load_ms")
    private Integer loadMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
