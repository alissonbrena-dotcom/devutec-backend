package pe.edu.utec.devutec.quality;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utec.devutec.model.contract.ContractStage;

import java.time.LocalDateTime;

// Una revisión de la web con el estándar de calidad de DEVUTEC (antes de la entrega final de un proyecto web).
// Se guardan todas: el cliente ve la última y el estudiante puede revisar de nuevo después de arreglar.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "quality_checks", indexes = @Index(name = "idx_quality_checks_stage", columnList = "stage_id, created_at"))
public class QualityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private ContractStage stage;

    @Column(nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QualityStatus status;

    // Puntajes de 0 a 100 (null si Google no respondió o no pudo abrir la web)
    private Integer performance;
    private Integer accessibility;

    @Column(name = "best_practices")
    private Integer bestPractices;

    private Integer seo;

    private Boolean https;

    // JSON con lo que no cumple, los puntos a mejorar de Google y las tareas de la IA (QualityCheckContent)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
