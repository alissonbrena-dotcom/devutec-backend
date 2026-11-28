package pe.edu.utec.devutec.pricing;

import java.math.BigDecimal;
import java.util.List;

// Lo que ve el estudiante en "Antes de postular" (el copiloto):
// estimate: horas, precio y plazo sugeridos del proyecto
// myHourlyRate / myMin / myMax: cuánto cobraría con SU tarifa por hora (null si no la llenó)
// underestimateBelow: si propone menos que esto, la app muestra la alerta de subestimación
// commonlyForgotten: lo que suele olvidarse en este tipo de proyecto
public record ProjectEstimateDTO(
        PriceEstimateDTO estimate,
        BigDecimal myHourlyRate,
        BigDecimal myMin,
        BigDecimal myMax,
        BigDecimal underestimateBelow,
        List<String> commonlyForgotten
) {
}
