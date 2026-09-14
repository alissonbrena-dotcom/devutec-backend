package pe.edu.utec.devutec.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    public ApplicationResponseDTO () {

    }

    public ApplicationResponseDTO(Long id, String projectTitle, String freelancerName, String message, BigDecimal proposedPrice, String status, LocalDateTime createdAt) {
        this.id = id;
        this.projectTitle = projectTitle;
        this.freelancerName = freelancerName;
        this.message = message;
        this.proposedPrice = proposedPrice;
        this.createdAt = createdAt;
    }
}
