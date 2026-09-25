package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.ProjectCreateDTO;
import pe.edu.utec.devutec.dto.ProjectResponseDTO;
import pe.edu.utec.devutec.dto.ProjectUpdateDTO;
import pe.edu.utec.devutec.dto.SkillResponseDTO;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.ProjectStatus;
import pe.edu.utec.devutec.model.Skill;
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
    private final CurrentUserService currentUserService;

    @Override
    public ProjectResponseDTO create(ProjectCreateDTO dto) {
        User client = currentUserService.getCurrentUser();

        Project project = new Project();
        project.setTitle(dto.getTitle());
        project.setDescription(dto.getDescription());
        project.setBudget(dto.getBudget());
        project.setDeadline(dto.getDeadline());
        project.setClientId(client.getId());
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
        return toResponseDTO(findEntityById(id));
    }

    @Override
    public ProjectResponseDTO update(Long id, ProjectUpdateDTO dto) {
        Project project = findEntityById(id);
        ensureCanModify(project);

        if (dto.getTitle() != null) project.setTitle(dto.getTitle());
        if (dto.getDescription() != null) project.setDescription(dto.getDescription());
        if (dto.getBudget() != null) project.setBudget(dto.getBudget());
        if (dto.getDeadline() != null) project.setDeadline(dto.getDeadline());
        if (dto.getSkillIds() != null) project.setSkills(resolveSkills(dto.getSkillIds()));

        return toResponseDTO(projectRepository.save(project));
    }

    @Override
    public void delete(Long id) {
        Project project = findEntityById(id);
        ensureCanModify(project);
        projectRepository.delete(project);
    }

    private Project findEntityById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con id: " + id));
    }

    private void ensureCanModify(Project project) {
        User user = currentUserService.getCurrentUser();
        boolean isOwner = user.getId().equals(project.getClientId());
        boolean isAdmin = user.getRol() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ForbiddenOperationException("Solo el cliente que publicó el proyecto puede modificarlo");
        }
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