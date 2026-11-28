package pe.edu.utec.devutec.moderation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utec.devutec.auth.domain.User;

import java.time.LocalDateTime;

// Un reporte para el admin: lo hace una persona (botón "Reportar") o el filtro de contacto cuando bloquea un texto.
// Con los reportes el admin ve quién intenta salirse de DEVUTEC y decide (advertencia, suspensión).
@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "reports", indexes = @Index(name = "idx_reports_reported", columnList = "reported_id"))
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quién reporta (null si fue el filtro automático)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    // A quién se reporta
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_id", nullable = false)
    private User reported;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    // Dónde pasó: "Contrato 12", "Postulación 5", "Proyecto 3 (descripción)"
    @Column(nullable = false, length = 120)
    private String place;

    // Lo que contó quien reporta, o un extracto del texto bloqueado (sin los números ni correos)
    @Column(length = 1000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
