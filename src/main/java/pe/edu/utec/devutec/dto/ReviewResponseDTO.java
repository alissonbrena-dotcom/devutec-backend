package pe.edu.utec.devutec.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponseDTO {
    private Long id;
    private Long contractId;
    private String projectTitle;
    private String authorName;
    private String revieweeName;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
