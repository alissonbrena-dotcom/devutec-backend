package pe.edu.utec.devutec.service;

import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.devutec.dto.UserResponseDTO;

public interface AvatarService {

    // Sube o reemplaza la foto de la persona con sesión iniciada y devuelve su usuario con la nueva avatarUrl
    UserResponseDTO upload(MultipartFile file);

    // Quita la foto de la persona con sesión iniciada (vuelve a mostrar sus iniciales)
    UserResponseDTO delete();

    // La foto de un usuario para mostrarla en una etiqueta <img>
    AvatarImage find(Long userId);

    record AvatarImage(byte[] bytes, String contentType) {
    }
}
