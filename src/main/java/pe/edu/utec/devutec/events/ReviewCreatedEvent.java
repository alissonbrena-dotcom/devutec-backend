package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ReviewCreatedEvent extends ApplicationEvent {

    private final Long reviewId;
    private final Long revieweeId;
    private final String revieweeEmail;
    private final String revieweeName;
    private final String authorName;
    private final String projectTitle;
    private final Integer rating;

    public ReviewCreatedEvent(Object source, Long reviewId, Long revieweeId, String revieweeEmail,
                              String revieweeName, String authorName, String projectTitle, Integer rating) {
        super(source);
        this.reviewId = reviewId;
        this.revieweeId = revieweeId;
        this.revieweeEmail = revieweeEmail;
        this.revieweeName = revieweeName;
        this.authorName = authorName;
        this.projectTitle = projectTitle;
        this.rating = rating;
    }
}
