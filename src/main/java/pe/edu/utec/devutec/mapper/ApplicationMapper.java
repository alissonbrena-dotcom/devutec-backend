package pe.edu.utec.devutec.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.dto.ApplicationResponseDTO;
import pe.edu.utec.devutec.model.application.Application;

@Component
public class ApplicationMaper {
    public ApplicationResponseDTO toResponse(Application application) {
        return new ApplicationResponseDTO(
                application.getId(),
                application.getProject().getTitle(),
                application.getFreelancer().getUser().getNombre(),
                application.getMessage(),
                application.getProposedPrice(),
                application.getStatus().name(),
                application.getCreatedAt()
        );
    }
}