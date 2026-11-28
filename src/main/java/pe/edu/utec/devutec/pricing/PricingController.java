package pe.edu.utec.devutec.pricing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Precios y horas de referencia. Requiere sesión iniciada (cualquier rol).
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    // Tabla de horas de referencia (código, nombre, categoría, horas mínimas y máximas)
    @GetMapping("/features")
    public ResponseEntity<List<FuncionalidadReferencia>> features() {
        return ResponseEntity.ok(pricingService.features());
    }

    // Copiloto del estudiante: horas, cuánto cobrar, alerta de subestimación y lo que suele olvidarse
    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ProjectEstimateDTO> estimateProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(pricingService.estimateProject(projectId));
    }
}
