package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.dto.FreelancerProfileResponseDTO;
import pe.edu.utec.devutec.dto.FreelancerProfileUpdateDTO;

public interface FreelancerProfileService {
    FreelancerProfileResponseDTO getMyProfile();
    FreelancerProfileResponseDTO updateMyProfile(FreelancerProfileUpdateDTO dto);
    FreelancerProfileResponseDTO findByUserId(Long userId);
}
