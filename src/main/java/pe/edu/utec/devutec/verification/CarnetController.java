package pe.edu.utec.devutec.verification;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CarnetController {

    private final CarnetService service;

    // El estudiante sube la foto de su carnet: multipart/form-data con el campo "file" (JPG o PNG, hasta 5 MB)
    @PutMapping(value = "/api/v1/users/me/carnet", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('FREELANCER')")
    public ResponseEntity<CarnetStatusDTO> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.upload(file));
    }

    // Estado de las verificaciones de quien inició sesión
    @GetMapping("/api/v1/users/me/carnet")
    public ResponseEntity<CarnetStatusDTO> myStatus() {
        return ResponseEntity.ok(service.myStatus());
    }

    // La foto del carnet: solo la persona y el admin (con el token, no es pública)
    @GetMapping("/api/v1/users/{userId}/carnet/image")
    public ResponseEntity<byte[]> image(@PathVariable Long userId) {
        StudentCard card = service.image(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(card.getContentType()))
                .cacheControl(CacheControl.noStore())
                .body(card.getImage());
    }

    // Admin: carnets por revisar (status=PENDING) o todos
    @GetMapping("/api/v1/admin/carnets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CarnetReviewItemDTO>> list(@RequestParam(required = false) CarnetStatus status) {
        return ResponseEntity.ok(service.list(status));
    }

    // Admin: aprobar o rechazar (con motivo)
    @PatchMapping("/api/v1/admin/carnets/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CarnetReviewItemDTO> review(@PathVariable Long userId,
                                                      @Valid @RequestBody CarnetReviewRequestDTO request) {
        return ResponseEntity.ok(service.review(userId, request));
    }
}
