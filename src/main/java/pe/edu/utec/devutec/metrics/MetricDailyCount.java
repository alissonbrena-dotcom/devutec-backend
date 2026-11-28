package pe.edu.utec.devutec.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

// Contador simple por web, día y tipo de evento (por ejemplo "web 9, 28 de noviembre, contactos: 4").
// Con esto sale el panel básico al instante sin depender de Amplitude. No guarda nada de cada visitante.
// test = el dato vino de localhost o de un link de prueba (mientras el estudiante desarrolla o en el demo)
@Entity
@Table(name = "metric_daily_counts",
        uniqueConstraints = @UniqueConstraint(name = "uk_metric_daily", columnNames = {"site_id", "day", "type", "test"}))
@Getter
@Setter
@NoArgsConstructor
public class MetricDailyCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(nullable = false)
    private LocalDate day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetricType type;

    @Column(nullable = false)
    private boolean test;

    @Column(nullable = false)
    private long total;

    // Solo para PERFORMANCE: suma de milisegundos, para sacar el promedio (sumValueMs ÷ total)
    @Column(name = "sum_value_ms", nullable = false)
    private long sumValueMs;
}
