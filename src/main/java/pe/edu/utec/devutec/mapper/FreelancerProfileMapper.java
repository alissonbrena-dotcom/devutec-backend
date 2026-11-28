package pe.edu.utec.devutec.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.dto.FreelancerProfileResponseDTO;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.verification.CarnetStatus;
import pe.edu.utec.devutec.verification.StudentEmailPolicy;

@Component
@RequiredArgsConstructor
public class FreelancerProfileMapper {

    private final StudentEmailPolicy emailPolicy;

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
                profile.getCalificacionPromedio(),
                AvatarUrls.of(profile.getUser()),
                profile.getPresentacion(),
                emailPolicy.isUniversityEmail(profile.getUser().getEmail()),
                profile.getCarnetStatus() == CarnetStatus.APPROVED
        );
    }
}
