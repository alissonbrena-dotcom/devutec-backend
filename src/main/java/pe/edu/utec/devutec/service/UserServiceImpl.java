package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.utec.devutec.dto.UserResponseDTO;
import pe.edu.utec.devutec.mapper.UserMapper;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final CurrentUserService currentUserService;
    private final UserMapper userMapper;

    @Override
    public UserResponseDTO getCurrentUser() {
        return userMapper.toResponse(currentUserService.getCurrentUser());
    }
}
