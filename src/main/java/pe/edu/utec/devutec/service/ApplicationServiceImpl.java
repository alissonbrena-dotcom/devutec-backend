package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.ApplicationRequestDTO;
import pe.edu.utec.devutec.dto.ApplicationResponseDTO;
import pe.edu.utec.devutec.events.ApplicationAcceptedEvent;
import pe.edu.utec.devutec.events.ApplicationCreatedEvent;
import pe.edu.utec.devutec.exceptions.DuplicateResourceException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.ApplicationMapper;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.ProjectStatus;
import pe.edu.utec.devutec.model.application.AppStatus;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.repository.ApplicationRepository;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;
import pe.edu.utec.devutec.repository.ProjectRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ProjectRepository projectRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;
    private final CurrentUserService currentUserService;
    private final ApplicationMapper applicationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ContractService contractService;

    @Override
    @Transactional
    public ApplicationResponseDTO apply(ApplicationRequestDTO dto) {
        FreelancerProfile freelancer = getCurrentFreelancer();
        Project project = findProject(dto.getProjectId());
        ensureProjectIsOpen(project);
        ensureNotAppliedYet(project, freelancer);

        Application application = new Application();
        application.setProject(project);
        application.setFreelancer(freelancer);
        application.setMessage(dto.getMessage());
        application.setProposedPrice(dto.getProposedPrice());
        Application saved = applicationRepository.save(application);

        eventPublisher.publishEvent(new ApplicationCreatedEvent(
                this, project.getClientId(), project.getTitle(), freelancer.getUser().getNombre()));
        return applicationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponseDTO> findByProject(Long projectId) {
        Project project = findProject(projectId);
        ensureIsProjectOwner(project);
        return applicationRepository.findByProjectId(projectId).stream()
                .map(applicationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponseDTO> findMine() {
        FreelancerProfile freelancer = getCurrentFreelancer();
        return applicationRepository.findByFreelancerId(freelancer.getId()).stream()
                .map(applicationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationResponseDTO findById(Long id) {
        Application application = findApplication(id);
        ensureCanView(application);
        return applicationMapper.toResponse(application);
    }

    @Override
    @Transactional
    public ApplicationResponseDTO accept(Long id) {
        Application application = findApplication(id);
        ensureIsProjectOwner(application.getProject());
        ensureIsPending(application);

        application.setStatus(AppStatus.ACCEPTED);
        rejectOtherPendingApplications(application);
        application.getProject().setStatus(ProjectStatus.IN_PROGRESS);
        contractService.createFromApplication(application);

        User freelancerUser = application.getFreelancer().getUser();
        eventPublisher.publishEvent(new ApplicationAcceptedEvent(
                this, freelancerUser.getEmail(), freelancerUser.getNombre(), application.getProject().getTitle()));
        return applicationMapper.toResponse(application);
    }

    @Override
    @Transactional
    public ApplicationResponseDTO reject(Long id) {
        Application application = findApplication(id);
        ensureIsProjectOwner(application.getProject());
        ensureIsPending(application);

        application.setStatus(AppStatus.REJECTED);
        return applicationMapper.toResponse(application);
    }

    @Override
    @Transactional
    public void withdraw(Long id) {
        Application application = findApplication(id);
        ensureIsApplicant(application);
        ensureIsPending(application);
        applicationRepository.delete(application);
    }

    private Application findApplication(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación no encontrada con id: " + id));
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con id: " + projectId));
    }

    private FreelancerProfile getCurrentFreelancer() {
        User user = currentUserService.getCurrentUser();
        return freelancerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ForbiddenOperationException("Solo los freelancers pueden realizar esta acción"));
    }

    private void rejectOtherPendingApplications(Application accepted) {
        applicationRepository.findByProjectIdAndStatus(accepted.getProject().getId(), AppStatus.PENDING).stream()
                .filter(other -> !other.getId().equals(accepted.getId()))
                .forEach(other -> other.setStatus(AppStatus.REJECTED));
    }

    private void ensureProjectIsOpen(Project project) {
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw new InvalidOperationException("El proyecto ya no acepta postulaciones");
        }
    }

    private void ensureNotAppliedYet(Project project, FreelancerProfile freelancer) {
        if (applicationRepository.existsByProjectIdAndFreelancerId(project.getId(), freelancer.getId())) {
            throw new DuplicateResourceException("Ya postulaste a este proyecto");
        }
    }

    private void ensureIsPending(Application application) {
        if (application.getStatus() != AppStatus.PENDING) {
            throw new InvalidOperationException("Solo se puede modificar una postulación en estado PENDING");
        }
    }

    private void ensureIsProjectOwner(Project project) {
        User user = currentUserService.getCurrentUser();
        if (!user.getId().equals(project.getClientId())) {
            throw new ForbiddenOperationException("Solo el cliente dueño del proyecto puede realizar esta acción");
        }
    }

    private void ensureIsApplicant(Application application) {
        User user = currentUserService.getCurrentUser();
        if (!user.getId().equals(application.getFreelancer().getUser().getId())) {
            throw new ForbiddenOperationException("Solo el freelancer que postuló puede realizar esta acción");
        }
    }

    private void ensureCanView(Application application) {
        User user = currentUserService.getCurrentUser();
        boolean isApplicant = user.getId().equals(application.getFreelancer().getUser().getId());
        boolean isProjectOwner = user.getId().equals(application.getProject().getClientId());
        if (!isApplicant && !isProjectOwner) {
            throw new ForbiddenOperationException("No tienes acceso a esta postulación");
        }
    }
}
