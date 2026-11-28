package pe.edu.utec.devutec.verification;

import java.time.LocalDateTime;

// Estado de las dos verificaciones del estudiante: el correo universitario y el carnet
public record CarnetStatusDTO(
        boolean universityEmail,
        CarnetStatus status,
        String rejectReason,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {
}
