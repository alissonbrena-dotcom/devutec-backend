package pe.edu.utec.devutec.verification;

import java.time.LocalDateTime;

// Un carnet en la lista del admin
public record CarnetReviewItemDTO(
        Long userId,
        String nombre,
        String email,
        boolean universityEmail,
        String universidad,
        CarnetStatus status,
        String rejectReason,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {
}
