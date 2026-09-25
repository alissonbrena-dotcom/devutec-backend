package pe.edu.utec.devutec.service;

import org.springframework.data.domain.Pageable;
import pe.edu.utec.devutec.dto.PageResponseDTO;
import pe.edu.utec.devutec.dto.ProjectCreateDTO;
import pe.edu.utec.devutec.dto.ProjectFilterDTO;
import pe.edu.utec.devutec.dto.ProjectResponseDTO;
import pe.edu.utec.devutec.dto.ProjectUpdateDTO;

public interface ProjectService {
    ProjectResponseDTO create(ProjectCreateDTO dto);
    PageResponseDTO<ProjectResponseDTO> findAll(ProjectFilterDTO filters, Pageable pageable);
    ProjectResponseDTO findById(Long id);
    ProjectResponseDTO update(Long id, ProjectUpdateDTO dto);
    void delete(Long id);
}