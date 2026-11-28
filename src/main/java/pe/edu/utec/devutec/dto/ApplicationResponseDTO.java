package pe.edu.utec.devutec.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class ApplicationResponseDTO {
    private Long id;
    private String projectTitle;
    private String freelancerName;
    private String message;
    private BigDecimal proposedPrice;
    private String status;
    private LocalDateTime createdAt;
    private Long projectId;
    private Long freelancerUserId;
    // Contraoferta de plazo del freelancer (null si acepta el del proyecto)
    private LocalDate proposedDeadline;
    // Presupuesto y fecha límite del proyecto, para comparar la contraoferta
    private BigDecimal projectBudget;
    private LocalDate projectDeadline;
}
