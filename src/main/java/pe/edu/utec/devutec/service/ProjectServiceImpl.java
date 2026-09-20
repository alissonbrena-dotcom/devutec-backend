package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.utec.devutec.dto.ProjectCreateDTO;
import pe.edu.utec.devutec.dto.ProjectResponseDTO;
import pe.edu.utec.devutec.dto.ProjectUpdateDTO;
import pe.edu.utec.devutec.dto.SkillResponseDTO;
import pe.edu.utec.devutec.entity.Project;
import pe.edu.utec.devutec.entity.ProjectStatus;
import pe.edu.utec.devutec.entity.Skill;
import pe.edu.utec.devutec.repository.ProjectRepository;
import pe.edu.utec.devutec.repository.SkillRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final SkillRepository skillRepository;

    @Override
    public ProjectResponseDTO create(ProjectCreateDTO dto) {
        Project project = new Project();
        project.setTitle(dto.getTitle());
        project.setDescription(dto.getDescription());
        project.setBudget(dto.getBudget());
        project.setDeadline(dto.getDeadline());
        project.setClientId(dto.getClientId());
        project.setStatus(ProjectStatus.OPEN);
        project.setCreatedAt(LocalDateTime.now());
        project.setSkills(resolveSkills(dto.getSkillIds()));

        Project saved = projectRepository.save(project);
        return toResponseDTO(saved);
    }

    @Override
    public List<ProjectResponseDTO> findAll() {
        return projectRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ProjectResponseDTO findById(Long id) {
        Project project = findEntityById(id);
        return toResponseDTO(project);
    }

    @Override
    public ProjectResponseDTO update(Long id, ProjectUpdateDTO dto) {
        Project project = findEntityById(id);

        if (dto.getTitle() != null) project.setTitle(dto.getTitle());
        if (dto.getDescription() != null) project.setDescription(dto.getDescription());
        if (dto.getBudget() != null) project.setBudget(dto.getBudget());
        if (dto.getDeadline() != null) project.setDeadline(dto.getDeadline());
        if (dto.getSkillIds() != null) project.setSkills(resolveSkills(dto.getSkillIds()));

        Project updated = projectRepository.save(project);
        return toResponseDTO(updated);
    }

    @Override
    public void delete(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new RuntimeException("Proyecto no encontrado con id: " + id);
        }
        projectRepository.deleteById(id);
    }

    private Project findEntityById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proyecto no encontrado con id: " + id));
    }

    private Set<Skill> resolveSkills(Set<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(skillRepository.findAllById(skillIds));
    }

    private ProjectResponseDTO toResponseDTO(Project project) {
        Set<SkillResponseDTO> skillDTOs = project.getSkills().stream()
                .map(skill -> new SkillResponseDTO(skill.getId(), skill.getName()))
                .collect(Collectors.toSet());

        return new ProjectResponseDTO(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getBudget(),
                project.getDeadline(),
                project.getClientId(),
                project.getStatus().name(),
                skillDTOs,
                project.getCreatedAt()
        );
    }
}