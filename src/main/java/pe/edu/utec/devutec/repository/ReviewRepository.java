package pe.edu.utec.devutec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.devutec.model.review.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
