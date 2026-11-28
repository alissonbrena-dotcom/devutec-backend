package pe.edu.utec.devutec.assets;

import java.util.List;

// Archivos de un proyecto. Quien no puede descargarlos (un estudiante que aún no tiene el contrato) solo ve cuántos hay.
public record ProjectAssetsDTO(boolean canDownload, long images, long pdfs, List<ProjectAssetDTO> files) {
}
