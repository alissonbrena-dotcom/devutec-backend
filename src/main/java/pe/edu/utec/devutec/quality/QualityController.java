package pe.edu.utec.devutec.quality;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/stages/{stageId}/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityService service;

    // Si la etapa exige la revisión y cuál fue la última (cliente y estudiante)
    @GetMapping
    public ResponseEntity<QualityOverviewDTO> overview(@PathVariable Long contractId, @PathVariable Long stageId) {
        return ResponseEntity.ok(service.overview(contractId, stageId));
    }

    // El estudiante revisa su web publicada con el estándar de DEVUTEC (tarda entre 10 y 40 segundos)
    @PostMapping("/checks")
    @PreAuthorize("hasRole('FREELANCER')")
    public ResponseEntity<QualityCheckResponseDTO> check(@PathVariable Long contractId, @PathVariable Long stageId,
                                                         @Valid @RequestBody QualityCheckRequestDTO request) {
        return ResponseEntity.ok(service.check(contractId, stageId, request.url()));
    }
}
