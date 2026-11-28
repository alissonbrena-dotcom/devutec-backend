package pe.edu.utec.devutec.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class CarnetServiceTest {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};

    private final StudentCardRepository cards = mock(StudentCardRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final Map<Long, StudentCard> stored = new HashMap<>();

    private User student;
    private User admin;
    private CarnetService service;

    @BeforeEach
    void setUp() {
        student = user(2L, "Sofía", "sofia@utec.edu.pe", Role.FREELANCER);
        student.setFreelancerProfile(new FreelancerProfile());
        student.getFreelancerProfile().setUser(student);
        student.getFreelancerProfile().setUniversidad("UTEC");
        admin = user(1L, "Admin", "admin@devutec.com", Role.ADMIN);
        when(currentUser.getCurrentUser()).thenReturn(student);
        when(users.findById(2L)).thenReturn(Optional.of(student));
        when(cards.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.<Long>getArgument(0))));
        when(cards.save(any())).thenAnswer(invocation -> {
            StudentCard card = invocation.getArgument(0);
            stored.put(card.getUserId(), card);
            return card;
        });
        service = new CarnetService(cards, users, currentUser, new StudentEmailPolicy(true, ".edu.pe"));
    }

    private static User user(Long id, String name, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setNombre(name);
        user.setEmail(email);
        user.setRol(role);
        return user;
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "carnet.jpg", "image/jpeg", bytes);
    }

    @Test
    void studentUploadsAJpgAndWaitsForReview() {
        CarnetStatusDTO status = service.upload(file(JPG));

        assertEquals(CarnetStatus.PENDING, status.status());
        assertTrue(status.universityEmail());
        assertEquals("image/jpeg", stored.get(2L).getContentType());
        assertEquals(CarnetStatus.PENDING, student.getFreelancerProfile().getCarnetStatus());
    }

    @Test
    void pngIsAcceptedButOtherFilesAreNot() {
        assertEquals("image/png", CarnetService.imageType(PNG));
        assertThrows(ApiException.class, () -> service.upload(file("%PDF-1.7 no es foto".getBytes())));
        assertThrows(ApiException.class, () -> service.upload(file(new byte[0])));
    }

    @Test
    void adminApproves_andTheProfileShowsTheBadge() {
        service.upload(file(JPG));

        CarnetReviewItemDTO item = service.review(2L, new CarnetReviewRequestDTO(true, null));

        assertEquals(CarnetStatus.APPROVED, item.status());
        assertEquals(CarnetStatus.APPROVED, student.getFreelancerProfile().getCarnetStatus());
        assertNotNull(stored.get(2L).getReviewedAt());
        // Ya verificado: no puede subir otro
        assertThrows(ApiException.class, () -> service.upload(file(JPG)));
    }

    @Test
    void rejectNeedsAReason_andTheStudentCanUploadAgain() {
        service.upload(file(JPG));
        assertThrows(ApiException.class, () -> service.review(2L, new CarnetReviewRequestDTO(false, " ")));

        service.review(2L, new CarnetReviewRequestDTO(false, "La foto está borrosa"));
        assertEquals("La foto está borrosa", service.myStatus().rejectReason());

        assertEquals(CarnetStatus.PENDING, service.upload(file(PNG)).status());
        assertNull(stored.get(2L).getRejectReason());
    }

    @Test
    void onlyStudentsUpload() {
        when(currentUser.getCurrentUser()).thenReturn(user(5L, "Mario", "mario@gmail.com", Role.CLIENT));
        assertThrows(ForbiddenOperationException.class, () -> service.upload(file(JPG)));
    }

    @Test
    void onlyTheOwnerAndTheAdminSeeThePhoto() {
        service.upload(file(JPG));
        assertNotNull(service.image(2L));

        when(currentUser.getCurrentUser()).thenReturn(admin);
        assertNotNull(service.image(2L));

        when(currentUser.getCurrentUser()).thenReturn(user(5L, "Mario", "mario@gmail.com", Role.CLIENT));
        assertThrows(ForbiddenOperationException.class, () -> service.image(2L));
    }

    @Test
    void adminListShowsWhoHasAUniversityEmail() {
        service.upload(file(JPG));
        when(cards.findByStatusOrderBySubmittedAtAsc(CarnetStatus.PENDING)).thenReturn(List.of(stored.get(2L)));

        List<CarnetReviewItemDTO> pending = service.list(CarnetStatus.PENDING);

        assertEquals(1, pending.size());
        assertTrue(pending.getFirst().universityEmail());
        assertEquals("UTEC", pending.getFirst().universidad());
    }

    @Test
    void emailPolicy() {
        StudentEmailPolicy policy = new StudentEmailPolicy(true, ".edu.pe");
        assertTrue(policy.isUniversityEmail("Ana@UTEC.edu.pe"));
        assertTrue(policy.isUniversityEmail("luis@pucp.edu.pe"));
        assertFalse(policy.isUniversityEmail("ana@gmail.com"));
        assertFalse(policy.isUniversityEmail("ana@edu.pe.hack.com"));
        assertThrows(ApiException.class, () -> policy.ensureStudentEmail("ana@gmail.com"));
        assertDoesNotThrow(() -> new StudentEmailPolicy(false, ".edu.pe").ensureStudentEmail("ana@gmail.com"));
    }
}
