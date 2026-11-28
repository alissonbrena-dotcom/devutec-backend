package pe.edu.utec.devutec.guide;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Mini guía de cada etapa, solo para el estudiante del contrato
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/stages/{stageId}/guide")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FREELANCER')")
public class StageGuideController {

    private final StageGuideService stageGuideService;

    // Devuelve la guía de la etapa; si todavía no existe, la IA la arma (una sola vez)
    @PostMapping
    public ResponseEntity<StageGuideResponseDTO> getOrCreate(@PathVariable Long contractId, @PathVariable Long stageId) {
        return ResponseEntity.ok(stageGuideService.getOrCreate(contractId, stageId));
    }

    @PatchMapping("/tasks/{taskId}")
    public ResponseEntity<StageGuideResponseDTO> updateTask(@PathVariable Long contractId, @PathVariable Long stageId,
                                                            @PathVariable Long taskId,
                                                            @Valid @RequestBody TaskUpdateDTO request) {
        return ResponseEntity.ok(stageGuideService.updateTask(contractId, stageId, taskId, request.done()));
    }
}
