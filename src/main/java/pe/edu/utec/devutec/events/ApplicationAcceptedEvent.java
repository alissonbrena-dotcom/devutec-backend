package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ApplicationAcceptedEvent extends ApplicationEvent {
    private  final String freelancerEmail;
    private final String freelancerName;
    private final String projectTitle;

    public ApplicationAcceptedEvent(Object source, String freelancerEmail, String freelancerName, String projectTitle) {
        super(source);
        this.freelancerEmail = freelancerEmail;
        this.freelancerName = freelancerName;
        this.projectTitle = projectTitle;
    }
}