package pe.edu.utec.devutec.quality;

// Estado de calidad de una etapa: si la entrega final la exige, los mínimos y la última revisión (o null)
public record QualityOverviewDTO(
        boolean required,
        boolean webProject,
        QualityService.Minimums minimums,
        QualityCheckResponseDTO last
) {
}
