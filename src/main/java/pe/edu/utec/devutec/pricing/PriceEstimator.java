package pe.edu.utec.devutec.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.model.Skill;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Calcula el precio y el plazo sugeridos de un proyecto, en cascada, para que la IA nunca invente números:
//   1. Proyectos parecidos terminados en DEVUTEC (comparten alguna habilidad).
//   2. Si no hay suficientes, todos los proyectos terminados.
//   3. Si tampoco, la tabla de horas de referencia × la tarifa por hora promedio de los estudiantes.
@Service
public class PriceEstimator {

    static final String SIMILAR_PROJECTS = "SIMILAR_PROJECTS";
    static final String CATEGORY = "CATEGORY";
    static final String HOURS = "HOURS";

    // Etapas con su porcentaje de pago, iguales a las del contrato (ContractStagePlan): suman 90.
    // El 10 % que falta es la bolsa de emergencia, que va aparte (ver PaymentPlanner).
    static final List<StageTemplate> STAGES = List.of(
            new StageTemplate("Descubrimiento", 10),
            new StageTemplate("Diseño", 30),
            new StageTemplate("Desarrollo", 40),
            new StageTemplate("Entrega final", 10));
    // Suma de los porcentajes de las etapas (90): las horas se reparten sobre esto, porque la bolsa no es trabajo
    static final int STAGES_PERCENT = STAGES.stream().mapToInt(StageTemplate::percent).sum();

    // Todo proyecto tiene reunión inicial y publicación, aunque la IA no las nombre
    static final List<String> ALWAYS_INCLUDED = List.of("DESCUBRIMIENTO", "DESPLIEGUE");

    private final FuncionalidadReferenciaRepository funcionalidades;
    private final ContractRepository contractRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;
    private final CommissionCalculator commission;
    private final BigDecimal defaultHourlyRate;
    private final int minSamples;
    private final int hoursPerWeek;

    public PriceEstimator(FuncionalidadReferenciaRepository funcionalidades,
                          ContractRepository contractRepository,
                          FreelancerProfileRepository freelancerProfileRepository,
                          CommissionCalculator commission,
                          @Value("${pricing.default-hourly-rate:25}") BigDecimal defaultHourlyRate,
                          @Value("${pricing.min-samples:3}") int minSamples,
                          @Value("${pricing.student-hours-per-week:12}") int hoursPerWeek) {
        this.funcionalidades = funcionalidades;
        this.contractRepository = contractRepository;
        this.freelancerProfileRepository = freelancerProfileRepository;
        this.commission = commission;
        this.defaultHourlyRate = defaultHourlyRate;
        this.minSamples = minSamples;
        this.hoursPerWeek = Math.max(1, hoursPerWeek);
    }

    @Transactional(readOnly = true)
    public PriceEstimateDTO estimate(Collection<Long> skillIds, List<FeatureQuantityDTO> requested) {
        List<PriceEstimateDTO.Feature> features = resolveFeatures(requested);
        int hoursMin = features.stream().mapToInt(PriceEstimateDTO.Feature::hoursMin).sum();
        int hoursMax = features.stream().mapToInt(PriceEstimateDTO.Feature::hoursMax).sum();
        BigDecimal hourlyRate = averageHourlyRate();
        List<PriceEstimateDTO.Stage> stages = stagesFor(hoursMin, hoursMax);

        List<Contract> finished = contractRepository.findByStatus(ContractStatus.CONFIRMED);
        Set<Long> wanted = skillIds == null ? Set.of() : Set.copyOf(skillIds);
        List<Contract> similar = wanted.isEmpty() ? List.of() : finished.stream()
                .filter(contract -> contract.getApplication().getProject().getSkills().stream()
                        .map(Skill::getId).anyMatch(wanted::contains))
                .toList();

        if (similar.size() >= minSamples) {
            return fromContracts(SIMILAR_PROJECTS, similar,
                    "Basado en " + similar.size() + " proyectos parecidos terminados en DEVUTEC.",
                    features, hoursMin, hoursMax, hourlyRate, stages);
        }
        if (finished.size() >= minSamples) {
            return fromContracts(CATEGORY, finished,
                    "Aún no hay suficientes proyectos parecidos; basado en " + finished.size()
                            + " proyectos terminados en DEVUTEC.",
                    features, hoursMin, hoursMax, hourlyRate, stages);
        }

        BigDecimal freelancerMin = roundToTen(hourlyRate.multiply(BigDecimal.valueOf(hoursMin)));
        BigDecimal freelancerMax = roundToTen(hourlyRate.multiply(BigDecimal.valueOf(hoursMax)));
        String explanation = "Aún no hay proyectos para comparar: estimamos entre " + hoursMin + " y " + hoursMax
                + " horas con la tarifa promedio de nuestros estudiantes (S/ " + hourlyRate.stripTrailingZeros().toPlainString()
                + " por hora).";
        return new PriceEstimateDTO(HOURS, 0, explanation, features, hoursMin, hoursMax, hourlyRate,
                freelancerMin, freelancerMax, commission.clientTotalOf(freelancerMin), commission.clientTotalOf(freelancerMax),
                weeksFor(hoursMin), weeksFor(hoursMax), stages);
    }

    // Las funcionalidades que la IA nombró, validadas contra la tabla: los códigos desconocidos se descartan
    List<PriceEstimateDTO.Feature> resolveFeatures(List<FeatureQuantityDTO> requested) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        ALWAYS_INCLUDED.forEach(code -> quantities.put(code, 1));
        if (requested != null) {
            for (FeatureQuantityDTO item : requested) {
                if (item == null || item.code() == null) continue;
                String code = item.code().trim().toUpperCase();
                int quantity = Math.max(1, Math.min(20, item.quantity()));
                quantities.merge(code, quantity, ALWAYS_INCLUDED.contains(code) ? (a, b) -> a : Integer::sum);
            }
        }
        Map<String, FuncionalidadReferencia> known = funcionalidades.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(FuncionalidadReferencia::getCodigo, Function.identity()));
        List<PriceEstimateDTO.Feature> features = new ArrayList<>();
        quantities.forEach((code, quantity) -> {
            FuncionalidadReferencia ref = known.get(code);
            if (ref != null) {
                features.add(new PriceEstimateDTO.Feature(code, ref.getNombre(), quantity,
                        ref.getHorasMin() * quantity, ref.getHorasMax() * quantity));
            }
        });
        return features;
    }

    private PriceEstimateDTO fromContracts(String source, List<Contract> contracts, String explanation,
                                           List<PriceEstimateDTO.Feature> features, int hoursMin, int hoursMax,
                                           BigDecimal hourlyRate, List<PriceEstimateDTO.Stage> stages) {
        List<BigDecimal> amounts = contracts.stream().map(contract -> contract.getPayment().getAmount()).sorted().toList();
        BigDecimal freelancerMin = roundToTen(percentile(amounts, 25));
        BigDecimal freelancerMax = roundToTen(percentile(amounts, 75));
        List<Integer> weeks = contracts.stream()
                .filter(contract -> contract.getStartedAt() != null && contract.getDeliveredAt() != null)
                .map(contract -> (int) Math.max(1, Math.ceil(
                        Duration.between(contract.getStartedAt(), contract.getDeliveredAt()).toDays() / 7.0)))
                .sorted().toList();
        int weeksMin = weeks.isEmpty() ? weeksFor(hoursMin) : weeks.get(0);
        int weeksMax = weeks.isEmpty() ? weeksFor(hoursMax) : weeks.get(weeks.size() - 1);
        return new PriceEstimateDTO(source, contracts.size(), explanation, features, hoursMin, hoursMax, hourlyRate,
                freelancerMin, freelancerMax, commission.clientTotalOf(freelancerMin), commission.clientTotalOf(freelancerMax),
                weeksMin, weeksMax, stages);
    }

    BigDecimal averageHourlyRate() {
        Double average = freelancerProfileRepository.averageHourlyRate();
        return average == null || average <= 0
                ? defaultHourlyRate
                : BigDecimal.valueOf(average).setScale(0, RoundingMode.HALF_UP);
    }

    int weeksFor(int hours) {
        return Math.max(1, (int) Math.ceil((double) hours / hoursPerWeek));
    }

    static List<PriceEstimateDTO.Stage> stagesFor(int hoursMin, int hoursMax) {
        return STAGES.stream()
                .map(stage -> new PriceEstimateDTO.Stage(stage.name(), stage.percent(),
                        (int) Math.round(hoursMin * stage.percent() / (double) STAGES_PERCENT),
                        (int) Math.round(hoursMax * stage.percent() / (double) STAGES_PERCENT)))
                .toList();
    }

    static BigDecimal percentile(List<BigDecimal> sorted, int percent) {
        if (sorted.size() < 4) return percent < 50 ? sorted.get(0) : sorted.get(sorted.size() - 1);
        int index = (int) Math.round((sorted.size() - 1) * percent / 100.0);
        return sorted.get(index);
    }

    static BigDecimal roundToTen(BigDecimal value) {
        return value.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN).setScale(2, RoundingMode.UNNECESSARY);
    }

    record StageTemplate(String name, int percent) {
    }
}
