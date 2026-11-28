package pe.edu.utec.devutec.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

// Segunda verificación del estudiante (además del correo .edu.pe): sube la foto de su carnet universitario y el
// admin la revisa. Si la aprueba, el perfil muestra «Carnet verificado». Así es más difícil duplicar cuentas.
@Service
@RequiredArgsConstructor
public class CarnetService {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final StudentCardRepository cardRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final StudentEmailPolicy emailPolicy;

    // ---------- Estudiante ----------

    @Transactional
    public CarnetStatusDTO upload(MultipartFile file) {
        User user = currentStudent();
        byte[] bytes;
        try {
            bytes = file == null ? null : file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No pudimos leer la imagen. Prueba de nuevo");
        }
        String contentType = imageType(bytes);
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La foto puede pesar hasta 5 MB");
        }

        StudentCard card = cardRepository.findById(user.getId()).orElseGet(StudentCard::new);
        if (card.getStatus() == CarnetStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Tu carnet ya está verificado");
        }
        card.setUserId(user.getId());
        card.setImage(bytes);
        card.setContentType(contentType);
        card.setStatus(CarnetStatus.PENDING);
        card.setRejectReason(null);
        card.setSubmittedAt(LocalDateTime.now());
        card.setReviewedAt(null);
        cardRepository.save(card);
        setProfileStatus(user, CarnetStatus.PENDING);
        return toStatus(user, card);
    }

    @Transactional(readOnly = true)
    public CarnetStatusDTO myStatus() {
        User user = currentUserService.getCurrentUser();
        return toStatus(user, cardRepository.findById(user.getId()).orElse(null));
    }

    // La foto solo la ven la persona y el admin
    @Transactional(readOnly = true)
    public StudentCard image(Long userId) {
        User current = currentUserService.getCurrentUser();
        if (current.getRol() != Role.ADMIN && !current.getId().equals(userId)) {
            throw new ForbiddenOperationException("Solo el admin puede ver el carnet de otra persona");
        }
        return cardRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Esta persona no subió su carnet"));
    }

    // ---------- Admin ----------

    @Transactional(readOnly = true)
    public List<CarnetReviewItemDTO> list(CarnetStatus status) {
        List<StudentCard> cards = status == null
                ? cardRepository.findAllByOrderBySubmittedAtDesc()
                : cardRepository.findByStatusOrderBySubmittedAtAsc(status);
        return cards.stream()
                .map(card -> userRepository.findById(card.getUserId())
                        .map(user -> toItem(user, card))
                        .orElse(null))
                .filter(item -> item != null)
                .toList();
    }

    @Transactional
    public CarnetReviewItemDTO review(Long userId, CarnetReviewRequestDTO request) {
        StudentCard card = cardRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Esta persona no subió su carnet"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + userId));
        boolean approved = Boolean.TRUE.equals(request.approved());
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        if (!approved && reason == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cuéntale al estudiante por qué se rechaza (por ejemplo, foto borrosa)");
        }
        card.setStatus(approved ? CarnetStatus.APPROVED : CarnetStatus.REJECTED);
        card.setRejectReason(approved ? null : reason);
        card.setReviewedAt(LocalDateTime.now());
        cardRepository.save(card);
        setProfileStatus(user, card.getStatus());
        return toItem(user, card);
    }

    // ---------- Utilidades ----------

    private User currentStudent() {
        User current = currentUserService.getCurrentUser();
        if (current.getRol() != Role.FREELANCER) {
            throw new ForbiddenOperationException("Solo los estudiantes verifican su carnet");
        }
        return userRepository.findById(current.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + current.getId()));
    }

    // El estado queda también en el perfil, para mostrar la insignia sin cargar la foto
    private void setProfileStatus(User user, CarnetStatus status) {
        FreelancerProfile profile = user.getFreelancerProfile();
        if (profile != null) profile.setCarnetStatus(status);
    }

    // Revisa los primeros bytes: solo JPG o PNG (no se confía en el nombre del archivo)
    static String imageType(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Sube una foto de tu carnet (JPG o PNG)");
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return MediaType.IMAGE_PNG_VALUE;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "La foto tiene que ser JPG o PNG");
    }

    private CarnetStatusDTO toStatus(User user, StudentCard card) {
        return new CarnetStatusDTO(
                emailPolicy.isUniversityEmail(user.getEmail()),
                card == null ? null : card.getStatus(),
                card == null ? null : card.getRejectReason(),
                card == null ? null : card.getSubmittedAt(),
                card == null ? null : card.getReviewedAt());
    }

    private CarnetReviewItemDTO toItem(User user, StudentCard card) {
        FreelancerProfile profile = user.getFreelancerProfile();
        return new CarnetReviewItemDTO(
                user.getId(),
                user.getNombre(),
                user.getEmail(),
                emailPolicy.isUniversityEmail(user.getEmail()),
                profile == null ? null : profile.getUniversidad(),
                card.getStatus(),
                card.getRejectReason(),
                card.getSubmittedAt(),
                card.getReviewedAt());
    }
}
