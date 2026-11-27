package pe.edu.utec.devutec.translation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utec.devutec.model.contract.Contract;

import java.time.LocalDateTime;

// Una traducción ya hecha. Se guarda para no volver a llamar a la IA con el mismo mensaje
// (ahorra gasto) y para que las dos personas siempre vean la misma explicación.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "translations", indexes = @Index(name = "idx_translations_lookup",
        columnList = "contract_id, direction, original_hash"))
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TranslationDirection direction;

    // Etapa a la que pertenece el mensaje (por ejemplo «Diseño»); puede no haber
    @Column(name = "stage_name", length = 60)
    private String stageName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String original;

    // SHA-256 de la dirección, la etapa y el mensaje: así se encuentra rápido una traducción repetida
    @Column(name = "original_hash", nullable = false, length = 64)
    private String originalHash;

    // El resultado de la IA ya revisado, en JSON (TranslationContent)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
