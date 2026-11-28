package pe.edu.utec.devutec.moderation;

import java.time.LocalDateTime;

// reportedReports: cuántos reportes tiene en total esa persona (para ver si es algo repetido)
public record ReportResponseDTO(Long id, String reporterName, Long reportedId, String reportedName,
                                long reportedReports, ReportReason reason, String place, String detail,
                                ReportStatus status, LocalDateTime createdAt) {
}
