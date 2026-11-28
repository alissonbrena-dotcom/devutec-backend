package pe.edu.utec.devutec.assets;

import java.time.LocalDateTime;

public record ProjectAssetDTO(Long id, String kind, String fileName, String contentType, int sizeBytes,
                              Integer pages, String summary, LocalDateTime createdAt) {

    static ProjectAssetDTO of(ProjectAsset asset) {
        return new ProjectAssetDTO(asset.getId(), asset.getKind().name(), asset.getFileName(), asset.getContentType(),
                asset.getSizeBytes(), asset.getPages(), asset.getAiSummary(), asset.getCreatedAt());
    }
}
