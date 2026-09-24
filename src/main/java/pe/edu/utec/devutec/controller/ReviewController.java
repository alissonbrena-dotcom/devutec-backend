package pe.edu.utec.devutec.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.devutec.dto.ReviewCreateDTO;
import pe.edu.utec.devutec.dto.ReviewResponseDTO;
import pe.edu.utec.devutec.service.ReviewService;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/contracts/{contractId}/reviews")
    public ResponseEntity<ReviewResponseDTO> create(@PathVariable Long contractId,
                                                    @Valid @RequestBody ReviewCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(contractId, dto));
    }

    @GetMapping("/contracts/{contractId}/reviews")
    public ResponseEntity<List<ReviewResponseDTO>> findByContract(@PathVariable Long contractId) {
        return ResponseEntity.ok(reviewService.findByContract(contractId));
    }
}
