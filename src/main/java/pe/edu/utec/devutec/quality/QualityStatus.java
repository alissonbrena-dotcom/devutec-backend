package pe.edu.utec.devutec.quality;

// Resultado de revisar la web con el estándar de calidad de DEVUTEC
public enum QualityStatus {
    PASSED,      // Cumple los mínimos: se puede hacer la entrega final
    FAILED,      // No cumple: el estudiante arregla y vuelve a revisar antes de la fecha de la etapa
    UNAVAILABLE  // Google no respondió: no se bloquea la entrega por una falla externa
}
