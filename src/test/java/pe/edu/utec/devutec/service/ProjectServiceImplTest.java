package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.PageResponseDTO;
import pe.edu.utec.devutec.dto.ProjectCreateDTO;
import pe.edu.utec.devutec.dto.ProjectResponseDTO;
import pe.edu.utec.devutec.dto.ProjectUpdateDTO;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.UnauthorizedException;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.ProjectStatus;
import pe.edu.utec.devutec.repository.ProjectRepository;
import pe.edu.utec.devutec.repository.SkillRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ProjectServiceImpl projectService;

    private User client;

    @BeforeEach
    void setUp() {
        client = buildUser(10L, Role.CLIENT);
    }

    // TEST 1 - Crear un proyecto guarda la ubicación, el cliente autenticado y el estado OPEN
    @Test
    void create_conLocation_deberiaGuardarlaConClienteYEstadoOpen() {
        // Given
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        ProjectCreateDTO dto = new ProjectCreateDTO(
                "Landing para cafetería", "Página simple", new BigDecimal("300.00"), null, "Barranco", null);

        // When
        ProjectResponseDTO response = projectService.create(dto);

        // Then
        assertEquals("Barranco", response.getLocation());
        assertEquals(10L, response.getClientId());
        assertEquals("OPEN", response.getStatus());
    }

    // TEST 2 - La ubicación es opcional al crear
    @Test
    void create_sinLocation_deberiaGuardarlaComoNull() {
        // Given
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        ProjectCreateDTO dto = new ProjectCreateDTO(
                "Landing para cafetería", null, new BigDecimal("300.00"), null, null, null);

        // When
        ProjectResponseDTO response = projectService.create(dto);

        // Then
        assertNull(response.getLocation());
    }

    // TEST 3 - Editar sin enviar ubicación mantiene la anterior
    @Test
    void update_sinLocation_deberiaMantenerLaAnterior() {
        // Given
        Project project = buildProject(1L, client.getId(), "Surco");
        mockFindAndSave(project);
        ProjectUpdateDTO dto = new ProjectUpdateDTO();
        dto.setTitle("Título nuevo");

        // When
        ProjectResponseDTO response = projectService.update(1L, dto);

        // Then
        assertEquals("Título nuevo", response.getTitle());
        assertEquals("Surco", response.getLocation());
    }

    // TEST 4 - Editar con ubicación la reemplaza
    @Test
    void update_conLocation_deberiaActualizarla() {
        // Given
        Project project = buildProject(1L, client.getId(), "Surco");
        mockFindAndSave(project);
        ProjectUpdateDTO dto = new ProjectUpdateDTO();
        dto.setLocation("San Isidro");

        // When
        ProjectResponseDTO response = projectService.update(1L, dto);

        // Then
        assertEquals("San Isidro", response.getLocation());
    }

    // TEST 5 - Un cliente que no es dueño del proyecto no puede editarlo
    @Test
    void update_deOtroCliente_deberiaLanzarForbidden() {
        // Given
        Project project = buildProject(1L, 99L, "Surco");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        // When + Then
        assertThrows(ForbiddenOperationException.class,
                () -> projectService.update(1L, new ProjectUpdateDTO()));
        verify(projectRepository, never()).save(any());
    }

    // TEST 6 - Mis proyectos busca solo los del cliente autenticado con la paginación recibida
    @Test
    void findMine_deberiaBuscarPorClienteAutenticado() {
        // Given
        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Project> page = new PageImpl<>(
                List.of(buildProject(3L, 10L, "Surco"), buildProject(2L, 10L, null)), pageable, 3);
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(projectRepository.findByClientId(10L, pageable)).thenReturn(page);

        // When
        PageResponseDTO<ProjectResponseDTO> response = projectService.findMine(pageable);

        // Then
        assertEquals(2, response.getContent().size());
        assertEquals(3L, response.getContent().get(0).getId());
        assertEquals("Surco", response.getContent().get(0).getLocation());
        assertEquals(0, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(3, response.getTotalElements());
        assertEquals(2, response.getTotalPages());
        assertFalse(response.isLast());
    }

    // TEST 7 - Un cliente sin proyectos recibe una página vacía
    @Test
    void findMine_sinProyectos_deberiaDevolverPaginaVacia() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(projectRepository.findByClientId(10L, pageable)).thenReturn(Page.empty(pageable));

        // When
        PageResponseDTO<ProjectResponseDTO> response = projectService.findMine(pageable);

        // Then
        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());
    }

    // TEST 8 - Sin sesión se propaga UnauthorizedException y no se consulta el repositorio
    @Test
    void findMine_sinSesion_deberiaLanzarUnauthorized() {
        // Given
        when(currentUserService.getCurrentUser())
                .thenThrow(new UnauthorizedException("Debes iniciar sesión para realizar esta acción"));

        // When + Then
        assertThrows(UnauthorizedException.class, () -> projectService.findMine(PageRequest.of(0, 10)));
        verify(projectRepository, never()).findByClientId(any(), any());
    }

    private void mockFindAndSave(Project project) {
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Project buildProject(Long id, Long clientId, String location) {
        Project project = new Project();
        project.setId(id);
        project.setTitle("Proyecto " + id);
        project.setBudget(new BigDecimal("100.00"));
        project.setLocation(location);
        project.setClientId(clientId);
        project.setStatus(ProjectStatus.OPEN);
        project.setCreatedAt(LocalDateTime.now());
        return project;
    }

    private static User buildUser(Long id, Role rol) {
        User user = new User();
        user.setId(id);
        user.setNombre("Café Aula 3");
        user.setEmail("cliente@test.com");
        user.setRol(rol);
        return user;
    }
}
