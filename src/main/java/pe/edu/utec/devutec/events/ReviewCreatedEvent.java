package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ReviewCreatedEvent extends ApplicationEvent {

    private final Long reviewId;
    private final Long revieweeId;

    public ReviewCreatedEvent(Object source, Long reviewId, Long revieweeId) {
        super(source);
        this.reviewId = reviewId;
        this.revieweeId = revieweeId;
    }
}
