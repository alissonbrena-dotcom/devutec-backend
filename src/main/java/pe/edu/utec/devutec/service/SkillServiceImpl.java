package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.utec.devutec.dto.SkillRequestDTO;
import pe.edu.utec.devutec.dto.SkillResponseDTO;
import pe.edu.utec.devutec.model.Skill;
import pe.edu.utec.devutec.repository.SkillRepository;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.exceptions.ConflictException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final SkillRepository skillRepository;

    @Override
    public SkillResponseDTO create(SkillRequestDTO dto) {
        if (skillRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new ConflictException("Ya existe una skill con el nombre: " + dto.getName());
        }
        Skill skill = new Skill();
        skill.setName(dto.getName());
        Skill saved = skillRepository.save(skill);
        return toResponseDTO(saved);
    }

    @Override
    public List<SkillResponseDTO> findAll() {
        return skillRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SkillResponseDTO findById(Long id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill no encontrada con id: " + id));
        return toResponseDTO(skill);
    }

    @Override
    public void delete(Long id) {
        if (!skillRepository.existsById(id)) {
            throw new ResourceNotFoundException("Skill no encontrada con id: " + id);
        }
        skillRepository.deleteById(id);
    }

    private SkillResponseDTO toResponseDTO(Skill skill) {
        return new SkillResponseDTO(skill.getId(), skill.getName());
    }
}