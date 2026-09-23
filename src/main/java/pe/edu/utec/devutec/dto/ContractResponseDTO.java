package pe.edu.utec.devutec.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ContractResponseDTO {
    private Long id;
    private String projectTitle;
    private String freelancerName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime deliveredAt;
}
