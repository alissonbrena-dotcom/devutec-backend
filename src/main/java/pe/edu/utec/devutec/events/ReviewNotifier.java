package pe.edu.utec.devutec.events;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.utec.devutec.service.EmailService;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReviewNotifier {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener
    public void onReviewCreated(ReviewCreatedEvent event) {
        emailService.sendHtmlEmail(
                event.getRevieweeEmail(),
                "Recibiste una nueva reseña",
                "review-received",
                Map.of(
                        "revieweeName", event.getRevieweeName(),
                        "authorName", event.getAuthorName(),
                        "projectTitle", event.getProjectTitle(),
                        "rating", event.getRating()
                )
        );
    }
}
