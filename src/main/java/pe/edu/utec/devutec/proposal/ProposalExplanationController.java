package pe.edu.utec.devutec.proposal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ProposalExplanationController {

    private final ProposalExplanationService service;

    // La IA le explica al cliente qué cambia en la propuesta de un estudiante (precio o plazo) y por qué.
    @PostMapping("/{id}/explanation")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ProposalExplanationDTO> explain(@PathVariable Long id) {
        return ResponseEntity.ok(service.explain(id));
    }
}
