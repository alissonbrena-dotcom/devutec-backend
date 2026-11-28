package pe.edu.utec.devutec.moderation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // Reportar a la otra persona de un contrato o postulación: {"contractId": 12, "reason": "PAGO_POR_FUERA", "detail": "..."}
    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER')")
    public ResponseEntity<ReportResponseDTO> create(@Valid @RequestBody ReportCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.create(dto));
    }

    // Los últimos 100 reportes (de personas y del filtro automático)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReportResponseDTO>> findLatest() {
        return ResponseEntity.ok(reportService.findLatest());
    }

    @PatchMapping("/{id}/reviewed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportResponseDTO> markReviewed(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.markReviewed(id));
    }
}
