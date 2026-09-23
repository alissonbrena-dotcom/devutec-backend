package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.dto.SkillRequestDTO;
import pe.edu.utec.devutec.dto.SkillResponseDTO;

import java.util.List;

public interface SkillService {
    SkillResponseDTO create(SkillRequestDTO dto);
    List<SkillResponseDTO> findAll();
    SkillResponseDTO findById(Long id);
    void delete(Long id);
}