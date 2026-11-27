package pe.edu.utec.devutec.assistant;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Asistente IA para crear un proyecto conversando. Solo clientes con sesión iniciada.
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class ProjectAssistantController {

    private final ProjectAssistantService projectAssistantService;

    @PostMapping("/project-chat")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ProjectAssistantResponseDTO> chat(@Valid @RequestBody ProjectAssistantRequestDTO request) {
        return ResponseEntity.ok(projectAssistantService.chat(request));
    }
}
