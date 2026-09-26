package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.ApplicationRequestDTO;
import pe.edu.utec.devutec.events.ApplicationAcceptedEvent;
import pe.edu.utec.devutec.events.ApplicationCreatedEvent;
import pe.edu.utec.devutec.exceptions.DuplicateResourceException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.ApplicationMapper;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.ProjectStatus;
import pe.edu.utec.devutec.model.application.AppStatus;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.repository.ApplicationRepository;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;
import pe.edu.utec.devutec.repository.ProjectRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceImplTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private FreelancerProfileRepository freelancerProfileRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ContractService contractService;

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    private User client;
    private User freelancerUser;
    private FreelancerProfile freelancer;
    private Project project;

    @BeforeEach
    void setUp() {
        client = createUser(1L, "Cliente Uno", Role.CLIENT);
        freelancerUser = createUser(2L, "Free Uno", Role.FREELANCER);

        freelancer = new FreelancerProfile();
        freelancer.setId(10L);
        freelancer.setUser(freelancerUser);

        project = new Project();
        project.setId(100L);
        project.setTitle("Web para restaurante");
        project.setClientId(client.getId());
        project.setStatus(ProjectStatus.OPEN);
    }

    // TEST 1 - Un freelancer postula a un proyecto abierto
    @Test
    void apply_conProyectoAbierto_deberiaGuardarPostulacionYPublicarEvento() {
        // Given
        ApplicationRequestDTO dto = new ApplicationRequestDTO(100L, "Tengo experiencia en Spring Boot", new BigDecimal("1200"));
        when(currentUserService.getCurrentUser()).thenReturn(freelancerUser);
        when(freelancerProfileRepository.findByUserId(2L)).thenReturn(Optional.of(freelancer));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(applicationRepository.existsByProjectIdAndFreelancerId(100L, 10L)).thenReturn(false);
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        applicationService.apply(dto);

        // Then
        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        assertEquals(project, captor.getValue().getProject());
        assertEquals(freelancer, captor.getValue().getFreelancer());
        assertEquals(new BigDecimal("1200"), captor.getValue().getProposedPrice());
        verify(eventPublisher).publishEvent(any(ApplicationCreatedEvent.class));
    }

    // TEST 2 - No se puede postular dos veces al mismo proyecto
    @Test
    void apply_siYaPostulo_deberiaLanzarDuplicateResource() {
        // Given
        ApplicationRequestDTO dto = new ApplicationRequestDTO(100L, "Tengo experiencia en Spring Boot", new BigDecimal("1200"));
        when(currentUserService.getCurrentUser()).thenReturn(freelancerUser);
        when(freelancerProfileRepository.findByUserId(2L)).thenReturn(Optional.of(freelancer));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(applicationRepository.existsByProjectIdAndFreelancerId(100L, 10L)).thenReturn(true);

        // When / Then
        assertThrows(DuplicateResourceException.class, () -> applicationService.apply(dto));
        verify(applicationRepository, never()).save(any());
    }

    // TEST 3 - No se puede postular a un proyecto que ya no está abierto
    @Test
    void apply_conProyectoEnProgreso_deberiaLanzarInvalidOperation() {
        // Given
        project.setStatus(ProjectStatus.IN_PROGRESS);
        ApplicationRequestDTO dto = new ApplicationRequestDTO(100L, "Tengo experiencia en Spring Boot", new BigDecimal("1200"));
        when(currentUserService.getCurrentUser()).thenReturn(freelancerUser);
        when(freelancerProfileRepository.findByUserId(2L)).thenReturn(Optional.of(freelancer));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        // When / Then
        assertThrows(InvalidOperationException.class, () -> applicationService.apply(dto));
        verify(applicationRepository, never()).save(any());
    }

    // TEST 4 - Un usuario sin perfil de freelancer no puede postular
    @Test
    void apply_sinPerfilDeFreelancer_deberiaLanzarForbidden() {
        // Given
        ApplicationRequestDTO dto = new ApplicationRequestDTO(100L, "Tengo experiencia en Spring Boot", new BigDecimal("1200"));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(freelancerProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(ForbiddenOperationException.class, () -> applicationService.apply(dto));
    }

    // TEST 5 - Al aceptar: la postulación queda ACCEPTED, las demás REJECTED, el proyecto IN_PROGRESS y se crea el contrato
    @Test
    void accept_porElDuenoDelProyecto_deberiaAceptarRechazarOtrasYCrearContrato() {
        // Given
        Application accepted = createApplication(1L, AppStatus.PENDING);
        Application other = createApplication(2L, AppStatus.PENDING);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(accepted));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(applicationRepository.findByProjectIdAndStatus(100L, AppStatus.PENDING)).thenReturn(List.of(accepted, other));

        // When
        applicationService.accept(1L);

        // Then
        assertEquals(AppStatus.ACCEPTED, accepted.getStatus());
        assertEquals(AppStatus.REJECTED, other.getStatus());
        assertEquals(ProjectStatus.IN_PROGRESS, project.getStatus());
        verify(contractService).createFromApplication(accepted);
        verify(eventPublisher).publishEvent(any(ApplicationAcceptedEvent.class));
    }

    // TEST 6 - Un cliente que no es dueño del proyecto no puede aceptar
    @Test
    void accept_porOtroCliente_deberiaLanzarForbidden() {
        // Given
        Application application = createApplication(1L, AppStatus.PENDING);
        User otherClient = createUser(3L, "Otro Cliente", Role.CLIENT);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.getCurrentUser()).thenReturn(otherClient);

        // When / Then
        assertThrows(ForbiddenOperationException.class, () -> applicationService.accept(1L));
        assertEquals(AppStatus.PENDING, application.getStatus());
        verify(contractService, never()).createFromApplication(any());
    }

    // TEST 7 - No se puede aceptar una postulación que ya no está pendiente
    @Test
    void accept_postulacionYaRechazada_deberiaLanzarInvalidOperation() {
        // Given
        Application application = createApplication(1L, AppStatus.REJECTED);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        // When / Then
        assertThrows(InvalidOperationException.class, () -> applicationService.accept(1L));
        verify(contractService, never()).createFromApplication(any());
    }

    // TEST 8 - El dueño del proyecto rechaza una postulación pendiente
    @Test
    void reject_porElDuenoDelProyecto_deberiaRechazarla() {
        // Given
        Application application = createApplication(1L, AppStatus.PENDING);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        // When
        applicationService.reject(1L);

        // Then
        assertEquals(AppStatus.REJECTED, application.getStatus());
    }

    // TEST 9 - El freelancer retira su postulación pendiente
    @Test
    void withdraw_porElFreelancerQuePostulo_deberiaEliminarla() {
        // Given
        Application application = createApplication(1L, AppStatus.PENDING);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.getCurrentUser()).thenReturn(freelancerUser);

        // When
        applicationService.withdraw(1L);

        // Then
        verify(applicationRepository).delete(application);
    }

    // TEST 10 - Otro freelancer no puede retirar una postulación ajena
    @Test
    void withdraw_porOtroFreelancer_deberiaLanzarForbidden() {
        // Given
        Application application = createApplication(1L, AppStatus.PENDING);
        User otherFreelancer = createUser(4L, "Free Dos", Role.FREELANCER);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.getCurrentUser()).thenReturn(otherFreelancer);

        // When / Then
        assertThrows(ForbiddenOperationException.class, () -> applicationService.withdraw(1L));
        verify(applicationRepository, never()).delete(any());
    }

    // TEST 11 - Buscar una postulación que no existe
    @Test
    void findById_postulacionInexistente_deberiaLanzarNotFound() {
        // Given
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(ResourceNotFoundException.class, () -> applicationService.findById(99L));
    }

    // TEST 12 - Un usuario ajeno no puede ver una postulación
    @Test
    void findById_usuarioAjeno_deberiaLanzarForbidden() {
        // Given
        Application application = createApplication(1L, AppStatus.PENDING);
        User stranger = createUser(5L, "Ajeno", Role.CLIENT);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.getCurrentUser()).thenReturn(stranger);

        // When / Then
        assertThrows(ForbiddenOperationException.class, () -> applicationService.findById(1L));
    }

    @Test
    void apply_conProyectoInexistente_deberiaLanzarNotFound() {
        // Given
        ApplicationRequestDTO dto = new ApplicationRequestDTO(999L, "Tengo experiencia en Spring Boot", new BigDecimal("1200"));
        when(currentUserService.getCurrentUser()).thenReturn(freelancerUser);
        when(freelancerProfileRepository.findByUserId(2L)).thenReturn(Optional.of(freelancer));
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> applicationService.apply(dto));
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void reject_postulacionYaAceptada_deberiaLanzarInvalidOperation() {
        // Given
        Application application = createApplication(1L, AppStatus.ACCEPTED);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        // When / Then
        assertThrows(InvalidOperationException.class, () -> applicationService.reject(1L));
        assertEquals(AppStatus.ACCEPTED, application.getStatus());
    }


    private User createUser(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setNombre(name);
        user.setEmail(name.toLowerCase().replace(" ", "") + "@test.com");
        user.setRol(role);
        return user;
    }

    private Application createApplication(Long id, AppStatus status) {
        Application application = new Application();
        application.setId(id);
        application.setProject(project);
        application.setFreelancer(freelancer);
        application.setMessage("Tengo experiencia en Spring Boot");
        application.setProposedPrice(new BigDecimal("1200"));
        application.setStatus(status);
        return application;
    }
}
