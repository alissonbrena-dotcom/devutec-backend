package pe.edu.utec.devutec.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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
}
