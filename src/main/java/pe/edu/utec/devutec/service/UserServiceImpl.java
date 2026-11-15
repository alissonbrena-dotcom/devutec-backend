package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.UserResponseDTO;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.UserMapper;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final CurrentUserService currentUserService;
    private final UserMapper userMapper;
    private final UserRepository userRepository;

    @Override
    public UserResponseDTO getCurrentUser() {
        return userMapper.toResponse(currentUserService.getCurrentUser());
    }

    @Override
    @Transactional
    public UserResponseDTO updatePhone(String celular) {
        String normalized = PhoneNumbers.normalize(celular);
        User user = editableCurrentUser();
        user.setCelular(normalized);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponseDTO deletePhone() {
        User user = editableCurrentUser();
        user.setCelular(null);
        return userMapper.toResponse(user);
    }

    // El usuario de la sesión, leído de nuevo dentro de la transacción para que el cambio se guarde solo
    private User editableCurrentUser() {
        Long id = currentUserService.getCurrentUser().getId();
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
    }
}
