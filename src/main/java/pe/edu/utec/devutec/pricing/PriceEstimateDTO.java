package pe.edu.utec.devutec.pricing;

import java.math.BigDecimal;
import java.util.List;

// Precio y plazo sugeridos para un proyecto, con el detalle de cómo se calcularon.
// source: SIMILAR_PROJECTS (proyectos parecidos), CATEGORY (todos los proyectos terminados) o HOURS (tabla de horas)
// sampleSize: cuántos proyectos se usaron (0 si salió de la tabla de horas)
// Los montos "freelancer" son lo que recibe el estudiante; los "client" ya incluyen la comisión.
public record PriceEstimateDTO(
        String source,
        int sampleSize,
        String explanation,
        List<Feature> features,
        int hoursMin,
        int hoursMax,
        BigDecimal hourlyRate,
        BigDecimal freelancerMin,
        BigDecimal freelancerMax,
        BigDecimal clientMin,
        BigDecimal clientMax,
        int weeksMin,
        int weeksMax,
        List<Stage> stages
) {
    public record Feature(String code, String name, int quantity, int hoursMin, int hoursMax) {
    }

    // Etapa sugerida con su porcentaje de pago y sus horas estimadas
    public record Stage(String name, int percent, int hoursMin, int hoursMax) {
    }
}
