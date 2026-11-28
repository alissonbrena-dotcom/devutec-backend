package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.moderation.ContactGuard;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import pe.edu.utec.devutec.pricing.FuncionalidadReferenciaRepository;
import pe.edu.utec.devutec.pricing.FuncionalidadReferencia;
import pe.edu.utec.devutec.pricing.FeatureQuantityDTO;
import pe.edu.utec.devutec.model.ProjectFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.*;
import pe.edu.utec.devutec.exceptions.ConflictException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.ProjectStatus;
import pe.edu.utec.devutec.model.Skill;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.repository.ProjectRepository;
import pe.edu.utec.devutec.repository.ProjectSpecifications;
import pe.edu.utec.devutec.repository.SkillRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final SkillRepository skillRepository;
    private final ContractRepository contractRepository;
    private final CurrentUserService currentUserService;
    private final ContactGuard contactGuard;
    private final UserRepository userRepository;
    private final FuncionalidadReferenciaRepository funcionalidadReferenciaRepository;

    @Override
    public ProjectResponseDTO create(ProjectCreateDTO dto) {
        User client = currentUserService.getCurrentUser();
        // El título y la descripción los ven todos los estudiantes: sin datos de contacto ni pedidos de salirse
        contactGuard.checkBeforeContract(client, "Proyecto nuevo · " + dto.getTitle(), dto.getTitle(), dto.getDescription());

        Project project = new Project();
        project.setTitle(dto.getTitle());
        project.setDescription(dto.getDescription());
        project.setBudget(dto.getBudget());
        project.setDeadline(dto.getDeadline());
        project.setLocation(dto.getLocation());
        project.setClientId(client.getId());
        project.setStatus(ProjectStatus.OPEN);
        project.setCreatedAt(LocalDateTime.now());
        project.setSkills(resolveSkills(dto.getSkillIds()));
        project.setCategory(dto.getCategory());
        project.setFeatures(resolveFeatures(dto.getFeatures()));

        Project saved = projectRepository.save(project);
        return toResponseDTO(saved, client);
    }

    @Override
    public PageResponseDTO<ProjectResponseDTO> findAll(ProjectFilterDTO filters, Pageable pageable) {
        Page<Project> projects = projectRepository.findAll(ProjectSpecifications.withFilters(filters), pageable);
        Map<Long, User> clients = clientsOf(projects.getContent());
        return PageResponseDTO.from(projects.map(project -> toResponseDTO(project, clients.get(project.getClientId()))));
    }

    @Override
    public PageResponseDTO<ProjectResponseDTO> findMine(Pageable pageable) {
        User client = currentUserService.getCurrentUser();
        Page<ProjectResponseDTO> page = projectRepository
                .findByClientId(client.getId(), pageable)
                .map(project -> toResponseDTO(project, client));
        return PageResponseDTO.from(page);
    }

    @Override
    public ProjectResponseDTO findById(Long id) {
        Project project = findEntityById(id);
        return toResponseDTO(project, findClient(project));
    }

    @Override
    public ProjectResponseDTO update(Long id, ProjectUpdateDTO dto) {
        Project project = findEntityById(id);
        ensureCanModify(project);
        contactGuard.checkBeforeContract(currentUserService.getCurrentUser(), "Proyecto " + id + " (edición)",
                dto.getTitle(), dto.getDescription());

        if (dto.getTitle() != null) project.setTitle(dto.getTitle());
        if (dto.getDescription() != null) project.setDescription(dto.getDescription());
        if (dto.getBudget() != null) project.setBudget(dto.getBudget());
        if (dto.getDeadline() != null) project.setDeadline(dto.getDeadline());
        if (dto.getLocation() != null) project.setLocation(dto.getLocation());
        if (dto.getSkillIds() != null) project.setSkills(resolveSkills(dto.getSkillIds()));

        return toResponseDTO(projectRepository.save(project), findClient(project));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Project project = findEntityById(id);
        ensureCanModify(project);
        ensureCanBeDeleted(project);
        // Las postulaciones del proyecto se eliminan en cascada (Project.applications)
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

    // Un proyecto cancelado vuelve a OPEN pero conserva su contrato cancelado, por eso se revisan ambas condiciones
    private void ensureCanBeDeleted(Project project) {
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw new ConflictException("Solo se puede eliminar un proyecto abierto (estado actual: " + project.getStatus() + ")");
        }
        if (contractRepository.existsByApplication_Project_Id(project.getId())) {
            throw new ConflictException("No se puede eliminar un proyecto que tiene contratos asociados");
        }
    }

    private Set<Skill> resolveSkills(Set<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(skillRepository.findAllById(skillIds));
    }

    private User findClient(Project project) {
        return project.getClientId() == null ? null : userRepository.findById(project.getClientId()).orElse(null);
    }

    // Clientes de una página de proyectos en una sola consulta (en vez de una por proyecto)
    private Map<Long, User> clientsOf(List<Project> projects) {
        Set<Long> clientIds = projects.stream()
                .map(Project::getClientId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (clientIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    // client puede ser null si la cuenta ya no existe: el proyecto se muestra igual, sin nombre
    // Solo se guardan las funcionalidades con un código que existe en la tabla de horas de referencia
    private List<ProjectFeature> resolveFeatures(List<FeatureQuantityDTO> features) {
        if (features == null || features.isEmpty()) return new ArrayList<>();
        Set<String> known = funcionalidadReferenciaRepository.findAll().stream()
                .map(FuncionalidadReferencia::getCodigo)
                .collect(Collectors.toSet());
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (FeatureQuantityDTO feature : features) {
            String code = feature.code().trim().toUpperCase();
            if (known.contains(code)) quantities.merge(code, Math.max(1, Math.min(20, feature.quantity())), Integer::sum);
        }
        List<ProjectFeature> result = new ArrayList<>();
        quantities.forEach((code, quantity) -> result.add(new ProjectFeature(code, quantity)));
        return result;
    }

    private ProjectResponseDTO toResponseDTO(Project project, User client) {
        Set<SkillResponseDTO> skillDTOs = project.getSkills().stream()
                .map(skill -> new SkillResponseDTO(skill.getId(), skill.getName()))
                .collect(Collectors.toSet());

        return new ProjectResponseDTO(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getBudget(),
                project.getDeadline(),
                project.getLocation(),
                project.getClientId(),
                project.getStatus().name(),
                skillDTOs,
                project.getCreatedAt(),
                client != null ? client.getNombre() : null,
                client != null ? client.getFechaRegistro() : null,
                client != null ? SocialLinksDTO.of(client) : null
        );
    }
}