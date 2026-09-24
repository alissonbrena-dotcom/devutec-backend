package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.dto.ApplicationRequestDTO;
import pe.edu.utec.devutec.dto.ApplicationResponseDTO;

import java.util.List;

public interface ApplicationService {
    ApplicationResponseDTO apply(ApplicationRequestDTO dto);
    List<ApplicationResponseDTO> findByProject(Long projectId);
    List<ApplicationResponseDTO> findMine();
    ApplicationResponseDTO findById(Long id);
    ApplicationResponseDTO accept(Long id);
    ApplicationResponseDTO reject(Long id);
    void withdraw(Long id);
}