package pe.edu.utec.devutec.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.dto.FreelancerProfileResponseDTO;
import pe.edu.utec.devutec.model.FreelancerProfile;

@Component
public class FreelancerProfileMapper {

    public FreelancerProfileResponseDTO toResponse(FreelancerProfile profile) {
        return new FreelancerProfileResponseDTO(
                profile.getUser().getId(),
                profile.getUser().getNombre(),
                profile.getUniversidad(),
                profile.getCiclo(),
                profile.getEgresado(),
                profile.getTarifaHora(),
                profile.getPortafolioUrl(),
                profile.getGithubUsername(),
                profile.getCalificacionPromedio()
        );
    }
}
