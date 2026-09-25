package pe.edu.utec.devutec.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.devutec.dto.SkillRequestDTO;
import pe.edu.utec.devutec.dto.SkillResponseDTO;
import pe.edu.utec.devutec.service.SkillService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<SkillResponseDTO> create(@Valid @RequestBody SkillRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<SkillResponseDTO>> findAll() {
        return ResponseEntity.ok(skillService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SkillResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(skillService.findById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        skillService.delete(id);
        return ResponseEntity.noContent().build();
    }
}