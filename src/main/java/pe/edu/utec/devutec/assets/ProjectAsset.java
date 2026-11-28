package pe.edu.utec.devutec.assets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Archivo del negocio que sube el cliente (logo, fotos o un PDF de máximo 2 páginas), ya revisado y limpiado.
// Se sube mientras arma el proyecto (projectId vacío) y se une al proyecto cuando lo publica.
@Entity
@Table(name = "project_assets", indexes = {
        @Index(name = "idx_project_assets_owner", columnList = "ownerId"),
        @Index(name = "idx_project_assets_project", columnList = "projectId")
})
@Getter
@Setter
@NoArgsConstructor
public class ProjectAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProjectAssetKind kind;

    @Column(nullable = false, length = 40)
    private String contentType;

    @Column(nullable = false, length = 120)
    private String fileName;

    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private Integer sizeBytes;

    private Integer pages;

    // Lo que la IA entendió del archivo (sin datos de contacto): lo usa el asistente y lo ve el estudiante
    @Column(length = 1000)
    private String aiSummary;

    // Texto del PDF para el asistente (no se muestra a nadie)
    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
