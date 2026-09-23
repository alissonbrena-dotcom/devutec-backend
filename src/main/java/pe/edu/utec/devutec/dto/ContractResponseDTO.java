package pe.edu.utec.devutec.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ContractResponseDTO {
    private Long id;
    private String projectTitle;
    private String freelancerName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime deliveredAt;

    public ContractResponseDTO () {

    }

    public ContractResponseDTO (Long id, String projectTitle, String freelancerName, String status, LocalDateTime startedAt, LocalDateTime deliveredAt) {
        this.id = id;
        this.projectTitle = projectTitle;
        this.freelancerName = freelancerName;
        this.status = status;
        this.startedAt = startedAt;
        this.deliveredAt = deliveredAt;
    }
}
