package pe.edu.utec.devutec.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.devutec.dto.ApplicationRequestDTO;
import pe.edu.utec.devutec.dto.ApplicationResponseDTO;
import pe.edu.utec.devutec.service.ApplicationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    @PreAuthorize("hasRole('FREELANCER')")
    public ResponseEntity<ApplicationResponseDTO> apply(@Valid @RequestBody ApplicationRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.apply(dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<ApplicationResponseDTO>> findByProject(@RequestParam Long projectId) {
        return ResponseEntity.ok(applicationService.findByProject(projectId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    public ResponseEntity<List<ApplicationResponseDTO>> findMine() {
        return ResponseEntity.ok(applicationService.findMine());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(applicationService.findById(id));
    }

    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ApplicationResponseDTO> accept(@PathVariable Long id) {
        return ResponseEntity.ok(applicationService.accept(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ApplicationResponseDTO> reject(@PathVariable Long id) {
        return ResponseEntity.ok(applicationService.reject(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('FREELANCER')")
    public ResponseEntity<Void> withdraw(@PathVariable Long id) {
        applicationService.withdraw(id);
        return ResponseEntity.noContent().build();
    }
}
