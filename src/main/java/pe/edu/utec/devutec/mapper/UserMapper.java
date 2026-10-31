package pe.edu.utec.devutec.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.UserResponseDTO;

@Component
public class UserMapper {

    public UserResponseDTO toResponse(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getNombre(),
                user.getEmail(),
                user.getRol().name()
        );
    }
}
