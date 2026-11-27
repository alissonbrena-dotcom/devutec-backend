package pe.edu.utec.devutec.translation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// IA traductora entre el cliente y el estudiante de un contrato
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/translations")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;

    @PostMapping
    public ResponseEntity<TranslationResponseDTO> translate(@PathVariable Long contractId,
                                                            @Valid @RequestBody TranslateRequestDTO request) {
        return ResponseEntity.ok(translationService.translate(contractId, request));
    }
}
