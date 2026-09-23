package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.dto.ProjectCreateDTO;
import pe.edu.utec.devutec.dto.ProjectResponseDTO;
import pe.edu.utec.devutec.dto.ProjectUpdateDTO;

import java.util.List;

public interface ProjectService {
    ProjectResponseDTO create(ProjectCreateDTO dto);
    List<ProjectResponseDTO> findAll();
    ProjectResponseDTO findById(Long id);
    ProjectResponseDTO update(Long id, ProjectUpdateDTO dto);
    void delete(Long id);
}