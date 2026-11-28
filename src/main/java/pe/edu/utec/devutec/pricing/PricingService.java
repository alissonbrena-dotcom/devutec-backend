package pe.edu.utec.devutec.pricing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.Skill;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;
import pe.edu.utec.devutec.repository.ProjectRepository;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

// Estimaciones para el copiloto del estudiante ("Antes de postular") y la tabla de horas de referencia
@Service
@RequiredArgsConstructor
public class PricingService {

    static final List<String> FORGOTTEN_GENERAL = List.of(
            "Dominio y hosting: acuerda con el cliente quién los paga y a nombre de quién quedan.",
            "Rondas de cambios: DEVUTEC permite hasta 2 por etapa; inclúyelas en tu tiempo.",
            "Contenido del cliente: fotos, textos y logo. Pídeselos al inicio para no quedarte esperando.",
            "Soporte después de entregar: define cuánto tiempo das para corregir errores.");
    static final List<String> FORGOTTEN_WEB = List.of(
            "Pruebas en celular: la mayoría de clientes de una mype entra desde el celular.");
    static final List<String> FORGOTTEN_APP = List.of(
            "Publicar en las tiendas de apps tiene cuotas y revisiones que pueden tardar días.");
    static final List<String> FORGOTTEN_AUTOMATIZACION = List.of(
            "Accesos del cliente: necesitarás entrar a sus cuentas o sistemas; pídeselos de forma segura y nunca por chat abierto.");

    private final PriceEstimator priceEstimator;
    private final FuncionalidadReferenciaRepository funcionalidades;
    private final ProjectRepository projectRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<FuncionalidadReferencia> features() {
        return funcionalidades.findAllByOrderByCategoriaAscCodigoAsc();
    }

    @Transactional(readOnly = true)
    public ProjectEstimateDTO estimateProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con id: " + projectId));
        List<FeatureQuantityDTO> features = project.getFeatures().stream()
                .map(feature -> new FeatureQuantityDTO(feature.getCode(), feature.getQuantity()))
                .toList();
        List<Long> skillIds = project.getSkills().stream().map(Skill::getId).toList();
        PriceEstimateDTO estimate = priceEstimator.estimate(skillIds, features);

        BigDecimal myRate = null;
        BigDecimal myMin = null;
        BigDecimal myMax = null;
        User user = currentUserService.getCurrentUser();
        if (user.getRol() == Role.FREELANCER) {
            myRate = freelancerProfileRepository.findByUserId(user.getId())
                    .map(FreelancerProfile::getTarifaHora)
                    .filter(rate -> rate.signum() > 0)
                    .orElse(null);
            if (myRate != null) {
                myMin = PriceEstimator.roundToTen(myRate.multiply(BigDecimal.valueOf(estimate.hoursMin())));
                myMax = PriceEstimator.roundToTen(myRate.multiply(BigDecimal.valueOf(estimate.hoursMax())));
            }
        }
        // Por debajo del 80 % del mínimo estimado, el estudiante probablemente está subestimando el trabajo
        BigDecimal underestimateBelow = estimate.freelancerMin().multiply(new BigDecimal("0.8")).setScale(2, RoundingMode.HALF_UP);
        return new ProjectEstimateDTO(estimate, myRate, myMin, myMax, underestimateBelow, forgottenFor(project.getCategory()));
    }

    static List<String> forgottenFor(String category) {
        List<String> items = new ArrayList<>(FORGOTTEN_GENERAL);
        if ("APP".equals(category)) items.addAll(FORGOTTEN_APP);
        else if ("AUTOMATIZACION".equals(category)) items.addAll(FORGOTTEN_AUTOMATIZACION);
        else items.addAll(FORGOTTEN_WEB);
        return items;
    }
}
