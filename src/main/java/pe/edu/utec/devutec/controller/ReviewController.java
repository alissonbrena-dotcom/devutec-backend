package pe.edu.utec.devutec.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.devutec.dto.PageResponseDTO;
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

    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<PageResponseDTO<ReviewResponseDTO>> findByReviewee(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page debe ser mayor o igual a 0") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size debe ser al menos 1")
            @Max(value = 50, message = "size no puede ser mayor a 50") int size) {
        return ResponseEntity.ok(reviewService.findByReviewee(userId, page, size));
    }
}
