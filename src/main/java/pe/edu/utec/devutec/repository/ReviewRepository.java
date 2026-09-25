package pe.edu.utec.devutec.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utec.devutec.model.review.Review;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByContract_IdAndAuthor_Id(Long contractId, Long authorId);

    List<Review> findByContract_IdOrderByCreatedAtDesc(Long contractId);

    @EntityGraph(attributePaths = {"author", "reviewee", "contract.application.project"})
    Page<Review> findByReviewee_Id(Long revieweeId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "reviewee", "contract.application.project"})
    Page<Review> findByReviewee_IdAndRatingGreaterThanEqual(Long revieweeId, Integer minRating, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewee.id = :revieweeId")
    Double averageRatingByRevieweeId(@Param("revieweeId") Long revieweeId);
}
