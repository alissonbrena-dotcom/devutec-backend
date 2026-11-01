package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.dto.FreelancerProfileResponseDTO;
import pe.edu.utec.devutec.dto.FreelancerProfileUpdateDTO;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.FreelancerProfileMapper;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;

@Service
@RequiredArgsConstructor
public class FreelancerProfileServiceImpl implements FreelancerProfileService {

    private final FreelancerProfileRepository freelancerProfileRepository;
    private final CurrentUserService currentUserService;
    private final FreelancerProfileMapper freelancerProfileMapper;

    @Override
    @Transactional(readOnly = true)
    public FreelancerProfileResponseDTO getMyProfile() {
        return freelancerProfileMapper.toResponse(findMyProfile());
    }

    @Override
    @Transactional
    public FreelancerProfileResponseDTO updateMyProfile(FreelancerProfileUpdateDTO dto) {
        FreelancerProfile profile = findMyProfile();

        if (dto.getUniversidad() != null) profile.setUniversidad(dto.getUniversidad());
        if (dto.getTarifaHora() != null) profile.setTarifaHora(dto.getTarifaHora());
        if (dto.getPortafolioUrl() != null) profile.setPortafolioUrl(dto.getPortafolioUrl());
        if (dto.getGithubUsername() != null) profile.setGithubUsername(dto.getGithubUsername());
        updateCicloYEgresado(profile, dto);

        return freelancerProfileMapper.toResponse(freelancerProfileRepository.save(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public FreelancerProfileResponseDTO findByUserId(Long userId) {
        FreelancerProfile profile = freelancerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de freelancer no encontrado para el usuario con id: " + userId));
        return freelancerProfileMapper.toResponse(profile);
    }

    private FreelancerProfile findMyProfile() {
        Long userId = currentUserService.getCurrentUser().getId();
        return freelancerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes un perfil de freelancer registrado"));
    }

    // Un egresado no tiene ciclo, y quien indica un ciclo deja de figurar como egresado
    private void updateCicloYEgresado(FreelancerProfile profile, FreelancerProfileUpdateDTO dto) {
        if (dto.getEgresado() != null) {
            profile.setEgresado(dto.getEgresado());
            if (dto.getEgresado()) profile.setCiclo(null);
        }
        if (dto.getCiclo() != null) {
            profile.setCiclo(dto.getCiclo());
            profile.setEgresado(false);
        }
    }
}
