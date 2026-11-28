package pe.edu.utec.devutec.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.devutec.dto.ChangeRequestDTO;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.dto.DeliveryRequestDTO;
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

    // Entrega la etapa actual del contrato
    @PatchMapping("/{id}/deliver")
    @PreAuthorize("hasRole('FREELANCER')")
    public ResponseEntity<ContractResponseDTO> deliver(@PathVariable Long id, @Valid @RequestBody DeliveryRequestDTO request) {
        return ResponseEntity.ok(contractService.deliver(id, request));
    }

    // Aprueba la etapa entregada; si es la última, confirma el contrato y libera el pago
    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ContractResponseDTO> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.confirm(id));
    }

    // Pide cambios en la etapa entregada (máximo 2 rondas por etapa)
    @PatchMapping("/{id}/request-changes")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ContractResponseDTO> requestChanges(@PathVariable Long id, @Valid @RequestBody ChangeRequestDTO request) {
        return ResponseEntity.ok(contractService.requestChanges(id, request));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ContractResponseDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.cancel(id));
    }
}