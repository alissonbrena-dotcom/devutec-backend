package pe.edu.utec.devutec.pricing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.Skill;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceEstimatorTest {

    private final FuncionalidadReferenciaRepository funcionalidades = mock(FuncionalidadReferenciaRepository.class);
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final FreelancerProfileRepository profiles = mock(FreelancerProfileRepository.class);
    private PriceEstimator estimator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(funcionalidades.findAllById(any())).thenAnswer(invocation -> {
            Collection<String> codes = invocation.getArgument(0);
            return FuncionalidadReferenciaSeeder.DEFAULTS.stream().filter(f -> codes.contains(f.getCodigo())).toList();
        });
        when(profiles.averageHourlyRate()).thenReturn(null);
        estimator = new PriceEstimator(funcionalidades, contracts, profiles,
                new CommissionCalculator(new BigDecimal("0.10")), new BigDecimal("25"), 3, 12);
    }

    @Test
    void sinProyectosTerminados_estimaPorHorasConLaTarifaPorDefecto() {
        when(contracts.findByStatus(ContractStatus.CONFIRMED)).thenReturn(List.of());

        PriceEstimateDTO estimate = estimator.estimate(Set.of(), List.of(
                new FeatureQuantityDTO("PAGINA_INFO", 3), new FeatureQuantityDTO("CARTA_CATALOGO", 1)));

        assertEquals("HOURS", estimate.source());
        // Descubrimiento (2–4) + despliegue (2–5) se agregan solos; 3 páginas (12–18) + carta (6–10)
        assertEquals(22, estimate.hoursMin());
        assertEquals(37, estimate.hoursMax());
        assertEquals(new BigDecimal("550.00"), estimate.freelancerMin());
        assertEquals(new BigDecimal("930.00"), estimate.freelancerMax());
        assertEquals(new BigDecimal("605.00"), estimate.clientMin());
        assertEquals(4, estimate.stages().size());
        // Etapas 10 / 30 / 40 / 10 (como el contrato) + 10 % de bolsa de emergencia aparte
        assertEquals(90, estimate.stages().stream().mapToInt(PriceEstimateDTO.Stage::percent).sum());
        assertEquals(10, estimate.stages().get(3).percent());
    }

    @Test
    void descartaLosCodigosQueNoEstanEnLaTabla() {
        when(contracts.findByStatus(ContractStatus.CONFIRMED)).thenReturn(List.of());

        PriceEstimateDTO estimate = estimator.estimate(Set.of(), List.of(new FeatureQuantityDTO("INVENTADO_POR_LA_IA", 5)));

        assertTrue(estimate.features().stream().noneMatch(f -> f.code().equals("INVENTADO_POR_LA_IA")));
    }

    @Test
    void conProyectosParecidos_usaSusMontos() {
        when(contracts.findByStatus(ContractStatus.CONFIRMED)).thenReturn(List.of(
                contract(1L, "700"), contract(1L, "800"), contract(1L, "900"), contract(2L, "3000")));

        PriceEstimateDTO estimate = estimator.estimate(Set.of(1L), List.of(new FeatureQuantityDTO("PAGINA_INFO", 1)));

        assertEquals("SIMILAR_PROJECTS", estimate.source());
        assertEquals(3, estimate.sampleSize());
        assertEquals(new BigDecimal("700.00"), estimate.freelancerMin());
        assertEquals(new BigDecimal("900.00"), estimate.freelancerMax());
        assertEquals(new BigDecimal("990.00"), estimate.clientMax());
    }

    @Test
    void sinParecidosPeroConProyectosTerminados_usaTodos() {
        when(contracts.findByStatus(ContractStatus.CONFIRMED)).thenReturn(List.of(
                contract(2L, "500"), contract(2L, "600"), contract(3L, "700")));

        PriceEstimateDTO estimate = estimator.estimate(Set.of(1L), List.of());

        assertEquals("CATEGORY", estimate.source());
        assertEquals(3, estimate.sampleSize());
    }

    private static Contract contract(Long skillId, String amount) {
        Skill skill = new Skill(skillId, "skill-" + skillId);
        Project project = new Project();
        project.setSkills(Set.of(skill));
        Application application = new Application();
        application.setProject(project);
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal(amount));
        Contract contract = new Contract();
        contract.setApplication(application);
        contract.setPayment(payment);
        contract.setStatus(ContractStatus.CONFIRMED);
        contract.setStartedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        contract.setDeliveredAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        return contract;
    }
}
