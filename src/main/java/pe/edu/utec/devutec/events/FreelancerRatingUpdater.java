package pe.edu.utec.devutec.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;
import pe.edu.utec.devutec.repository.ReviewRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreelancerRatingUpdater {

    private final FreelancerProfileRepository freelancerProfileRepository;
    private final ReviewRepository reviewRepository;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewCreated(ReviewCreatedEvent event) {
        freelancerProfileRepository.findByUserId(event.getRevieweeId()).ifPresent(profile -> {
            Double average = reviewRepository.averageRatingByRevieweeId(event.getRevieweeId());
            profile.setCalificacionPromedio(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
            freelancerProfileRepository.save(profile);
            log.info("Calificación promedio del freelancer {} actualizada a {}",
                    event.getRevieweeId(), profile.getCalificacionPromedio());
        });
    }
}
