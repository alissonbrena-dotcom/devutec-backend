package pe.edu.utec.devutec.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.dto.ReviewResponseDTO;
import pe.edu.utec.devutec.model.review.Review;

@Component
public class ReviewMapper {

    public ReviewResponseDTO toResponse(Review review) {
        return new ReviewResponseDTO(
                review.getId(),
                review.getContract().getId(),
                review.getContract().getApplication().getProject().getTitle(),
                review.getAuthor().getNombre(),
                review.getReviewee().getNombre(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
