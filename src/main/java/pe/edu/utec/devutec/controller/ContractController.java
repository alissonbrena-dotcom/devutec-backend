package pe.edu.utec.devutec.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.service.ContractService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping("/me")
    public ResponseEntity<List<ContractResponseDTO>> findMine() {
        return ResponseEntity.ok(contractService.findMine());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContractResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.findById(id));
    }

    @PatchMapping("/{id}/deliver")
    @PreAuthorize("hasRole('FREELANCER')")
    public ResponseEntity<ContractResponseDTO> deliver(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.deliver(id));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ContractResponseDTO> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.confirm(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ContractResponseDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.cancel(id));
    }
}