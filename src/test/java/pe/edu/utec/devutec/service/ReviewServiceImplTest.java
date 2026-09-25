package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pe.edu.utec.devutec.auth.domain.FreelancerProfile;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.PageResponseDTO;
import pe.edu.utec.devutec.dto.ReviewCreateDTO;
import pe.edu.utec.devutec.dto.ReviewResponseDTO;
import pe.edu.utec.devutec.events.ReviewCreatedEvent;
import pe.edu.utec.devutec.exceptions.DuplicateResourceException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.ReviewMapper;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.model.review.Review;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.repository.ReviewRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    private static final Long CONTRACT_ID = 1L;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private User client;
    private User freelancer;
    private Contract contract;

    @BeforeEach
    void setUp() {
        client = buildUser(10L, "Café Aula 3", "cliente@test.com");
        freelancer = buildUser(20L, "Camila Ríos", "camila@test.com");
        contract = buildContract(client, freelancer, ContractStatus.CONFIRMED);
    }

    // TEST 1 - El cliente reseña al freelancer de un contrato confirmado
    @Test
    void create_clienteConContratoConfirmado_deberiaReseñarAlFreelancer() {
        // Given
        givenCurrentUser(client);
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
        when(reviewRepository.existsByContract_IdAndAuthor_Id(CONTRACT_ID, client.getId())).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(reviewMapper.toResponse(any(Review.class))).thenReturn(new ReviewResponseDTO());

        // When
        reviewService.create(CONTRACT_ID, new ReviewCreateDTO(5, "Excelente trabajo"));

        // Then
        Review saved = captureSavedReview();
        assertEquals(client, saved.getAuthor());
        assertEquals(freelancer, saved.getReviewee());
        assertEquals(5, saved.getRating());
        verify(eventPublisher).publishEvent(any(ReviewCreatedEvent.class));
    }

    // TEST 2 - El freelancer reseña al cliente (se busca en UserRepository porque Project solo guarda clientId)
    @Test
    void create_freelancerConContratoConfirmado_deberiaReseñarAlCliente() {
        // Given
        givenCurrentUser(freelancer);
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(reviewRepository.existsByContract_IdAndAuthor_Id(CONTRACT_ID, freelancer.getId())).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(reviewMapper.toResponse(any(Review.class))).thenReturn(new ReviewResponseDTO());

        // When
        reviewService.create(CONTRACT_ID, new ReviewCreateDTO(4, "Cliente muy claro"));

        // Then
        Review saved = captureSavedReview();
        assertEquals(freelancer, saved.getAuthor());
        assertEquals(client, saved.getReviewee());
    }

    // TEST 3 - El evento lleva los datos necesarios para el correo y el promedio
    @Test
    void create_deberiaPublicarEventoConDatosDelReseñado() {
        // Given
        givenCurrentUser(client);
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
        when(reviewRepository.existsByContract_IdAndAuthor_Id(CONTRACT_ID, client.getId())).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(reviewMapper.toResponse(any(Review.class))).thenReturn(new ReviewResponseDTO());

        // When
        reviewService.create(CONTRACT_ID, new ReviewCreateDTO(5, null));

        // Then
        ArgumentCaptor<ReviewCreatedEvent> captor = ArgumentCaptor.forClass(ReviewCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ReviewCreatedEvent event = captor.getValue();
        assertEquals(freelancer.getId(), event.getRevieweeId());
        assertEquals("camila@test.com", event.getRevieweeEmail());
        assertEquals("Café Aula 3", event.getAuthorName());
        assertEquals("Landing page", event.getProjectTitle());
        assertEquals(5, event.getRating());
    }

    // TEST 4 - Un contrato que no existe lanza ResourceNotFoundException
    @Test
    void create_contratoInexistente_deberiaLanzarNotFound() {
        // Given
        when(contractRepository.findById(99L)).thenReturn(Optional.empty());

        // When + Then
        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.create(99L, new ReviewCreateDTO(5, null)));
        verify(reviewRepository, never()).save(any());
    }

    // TEST 5 - Un usuario ajeno al contrato no puede reseñar
    @Test
    void create_usuarioAjenoAlContrato_deberiaLanzarForbidden() {
        // Given
        givenCurrentUser(buildUser(99L, "Intruso", "intruso@test.com"));
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));

        // When + Then
        assertThrows(ForbiddenOperationException.class,
                () -> reviewService.create(CONTRACT_ID, new ReviewCreateDTO(1, null)));
        verify(reviewRepository, never()).save(any());
    }

    // TEST 6 - No se puede reseñar un contrato que aún no está confirmado
    @Test
    void create_contratoNoConfirmado_deberiaLanzarInvalidOperation() {
        // Given
        contract.setStatus(ContractStatus.DELIVERED);
        givenCurrentUser(client);
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));

        // When + Then
        assertThrows(InvalidOperationException.class,
                () -> reviewService.create(CONTRACT_ID, new ReviewCreateDTO(5, null)));
        verify(reviewRepository, never()).save(any());
    }

    // TEST 7 - Una segunda reseña del mismo autor al mismo contrato lanza DuplicateResourceException
    @Test
    void create_reseñaDuplicada_deberiaLanzarDuplicate() {
        // Given
        givenCurrentUser(client);
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
        when(reviewRepository.existsByContract_IdAndAuthor_Id(CONTRACT_ID, client.getId())).thenReturn(true);

        // When + Then
        assertThrows(DuplicateResourceException.class,
                () -> reviewService.create(CONTRACT_ID, new ReviewCreateDTO(5, null)));
        verify(reviewRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // TEST 8 - Solo los participantes pueden ver las reseñas de un contrato
    @Test
    void findByContract_usuarioAjeno_deberiaLanzarForbidden() {
        // Given
        givenCurrentUser(buildUser(99L, "Intruso", "intruso@test.com"));
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));

        // When + Then
        assertThrows(ForbiddenOperationException.class, () -> reviewService.findByContract(CONTRACT_ID));
    }

    // TEST 9 - Las reseñas de un usuario inexistente lanzan ResourceNotFoundException
    @Test
    void findByReviewee_usuarioInexistente_deberiaLanzarNotFound() {
        // Given
        when(userRepository.existsById(99L)).thenReturn(false);

        // When + Then
        assertThrows(ResourceNotFoundException.class, () -> reviewService.findByReviewee(99L, null, 0, 10));
    }

    // TEST 10 - Sin filtro se pagina con el tamaño pedido y de más reciente a más antigua
    @Test
    void findByReviewee_sinFiltro_deberiaPaginarOrdenadoPorFecha() {
        // Given
        when(userRepository.existsById(freelancer.getId())).thenReturn(true);
        when(reviewRepository.findByReviewee_Id(eq(freelancer.getId()), any(Pageable.class)))
                .thenAnswer(inv -> emptyPage(inv.getArgument(1)));

        // When
        PageResponseDTO<ReviewResponseDTO> result = reviewService.findByReviewee(freelancer.getId(), null, 2, 5);

        // Then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findByReviewee_Id(eq(freelancer.getId()), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(5, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("createdAt").getDirection());
        assertEquals(2, result.getPage());
        assertEquals(5, result.getSize());
    }

    // TEST 11 - Con minRating se usa la consulta filtrada
    @Test
    void findByReviewee_conMinRating_deberiaUsarConsultaFiltrada() {
        // Given
        when(userRepository.existsById(freelancer.getId())).thenReturn(true);
        when(reviewRepository.findByReviewee_IdAndRatingGreaterThanEqual(eq(freelancer.getId()), eq(4), any(Pageable.class)))
                .thenAnswer(inv -> emptyPage(inv.getArgument(2)));

        // When
        reviewService.findByReviewee(freelancer.getId(), 4, 0, 10);

        // Then
        verify(reviewRepository).findByReviewee_IdAndRatingGreaterThanEqual(eq(freelancer.getId()), eq(4), any(Pageable.class));
        verify(reviewRepository, never()).findByReviewee_Id(any(), any());
    }

    private void givenCurrentUser(User user) {
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    private Review captureSavedReview() {
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Review withId(Review review) {
        review.setId(100L);
        return review;
    }

    private static Page<Review> emptyPage(Pageable pageable) {
        return new PageImpl<>(List.of(), pageable, 0);
    }

    private static User buildUser(Long id, String nombre, String email) {
        User user = new User();
        user.setId(id);
        user.setNombre(nombre);
        user.setEmail(email);
        return user;
    }

    private static Contract buildContract(User client, User freelancer, ContractStatus status) {
        Project project = new Project();
        project.setTitle("Landing page");
        project.setClientId(client.getId());

        FreelancerProfile profile = new FreelancerProfile();
        profile.setUser(freelancer);

        Application application = new Application();
        application.setProject(project);
        application.setFreelancer(profile);

        Contract contract = new Contract();
        contract.setId(CONTRACT_ID);
        contract.setApplication(application);
        contract.setStatus(status);
        return contract;
    }
}
