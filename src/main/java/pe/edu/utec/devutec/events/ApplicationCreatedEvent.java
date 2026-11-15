package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
@Getter

public class ApplicationCreatedEvent extends ApplicationEvent {
    private final Long clientId;
    private final String projectTitle;
    private  final  String freelancerName;
    // Para el link "Ver postulantes" del correo
    private final Long projectId;

    public ApplicationCreatedEvent(Object source, Long clientId, String projectTitle, String freelancerName, Long projectId) {
        super(source);
        this.projectId = projectId;
        this.clientId = clientId;
        this.projectTitle = projectTitle;
        this.freelancerName = freelancerName;
    }
}