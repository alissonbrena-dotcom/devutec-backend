package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.UserResponseDTO;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.UserMapper;
import pe.edu.utec.devutec.model.UserAvatar;
import pe.edu.utec.devutec.repository.UserAvatarRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AvatarServiceImpl implements AvatarService {

    // Foto fija del administrador: el isotipo de DevUTEC
    static final String ADMIN_AVATAR = "avatar/admin.png";

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final UserAvatarRepository avatarRepository;
    private final AvatarImageProcessor processor;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponseDTO upload(MultipartFile file) {
        User user = editableCurrentUser();
        byte[] original;
        try {
            original = file == null ? null : file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No pudimos leer la imagen. Prueba de nuevo");
        }
        avatarRepository.save(new UserAvatar(user.getId(), processor.process(original)));
        user.setAvatarUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponseDTO delete() {
        User user = editableCurrentUser();
        if (avatarRepository.existsById(user.getId())) {
            avatarRepository.deleteById(user.getId());
        }
        user.setAvatarUpdatedAt(null);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AvatarImage find(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + userId));
        if (user.getRol() == Role.ADMIN) {
            return new AvatarImage(adminAvatar(), MediaType.IMAGE_PNG_VALUE);
        }
        return avatarRepository.findById(userId)
                .map(avatar -> new AvatarImage(avatar.getImage(), MediaType.IMAGE_JPEG_VALUE))
                .orElseThrow(() -> new ResourceNotFoundException("Esta persona no tiene foto de perfil"));
    }

    // El usuario de la sesión, leído de nuevo dentro de la transacción para que el cambio de fecha se guarde solo.
    // El administrador tiene una foto fija y no puede cambiarla.
    private User editableCurrentUser() {
        User current = currentUserService.getCurrentUser();
        if (current.getRol() == Role.ADMIN) {
            throw new ForbiddenOperationException("La foto del administrador es la de DevUTEC y no se puede cambiar");
        }
        return userRepository.findById(current.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + current.getId()));
    }

    private byte[] adminAvatar() {
        try (InputStream input = new ClassPathResource(ADMIN_AVATAR).getInputStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Falta la foto del administrador en " + ADMIN_AVATAR, e);
        }
    }
}
