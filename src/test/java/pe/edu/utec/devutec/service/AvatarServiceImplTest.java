package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.UserResponseDTO;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.UserMapper;
import pe.edu.utec.devutec.model.UserAvatar;
import pe.edu.utec.devutec.repository.UserAvatarRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvatarServiceImplTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAvatarRepository avatarRepository;

    @Mock
    private AvatarImageProcessor processor;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @InjectMocks
    private AvatarServiceImpl avatarService;

    private static User user(Long id, Role rol) {
        User user = new User();
        user.setId(id);
        user.setNombre("Camila Ríos");
        user.setEmail("camila@test.com");
        user.setRol(rol);
        return user;
    }

    private static MockMultipartFile photo() {
        return new MockMultipartFile("file", "foto.png", "image/png", new byte[]{1, 2, 3});
    }

    // TEST 1: al subir una foto se guarda la versión procesada y la respuesta trae la nueva avatarUrl
    @Test
    void upload_freelancer_deberiaGuardarLaFotoProcesada() {
        User camila = user(5L, Role.FREELANCER);
        when(currentUserService.getCurrentUser()).thenReturn(camila);
        when(userRepository.findById(5L)).thenReturn(Optional.of(camila));
        when(processor.process(any())).thenReturn(new byte[]{9, 9});

        UserResponseDTO response = avatarService.upload(photo());

        ArgumentCaptor<UserAvatar> saved = ArgumentCaptor.forClass(UserAvatar.class);
        verify(avatarRepository).save(saved.capture());
        assertEquals(5L, saved.getValue().getUserId());
        assertArrayEquals(new byte[]{9, 9}, saved.getValue().getImage());
        assertNotNull(camila.getAvatarUpdatedAt());
        assertTrue(response.getAvatarUrl().startsWith("/api/v1/users/5/avatar?v="));
    }

    // TEST 2: el administrador no puede cambiar su foto
    @Test
    void upload_admin_deberiaLanzarForbidden() {
        when(currentUserService.getCurrentUser()).thenReturn(user(1L, Role.ADMIN));

        assertThrows(ForbiddenOperationException.class, () -> avatarService.upload(photo()));
        verify(avatarRepository, never()).save(any());
    }

    // TEST 3: al quitar la foto se borra y la avatarUrl vuelve a null (se ven las iniciales)
    @Test
    void delete_conFoto_deberiaBorrarlaYDejarAvatarUrlEnNull() {
        User camila = user(5L, Role.CLIENT);
        camila.setAvatarUpdatedAt(LocalDateTime.now());
        when(currentUserService.getCurrentUser()).thenReturn(camila);
        when(userRepository.findById(5L)).thenReturn(Optional.of(camila));
        when(avatarRepository.existsById(5L)).thenReturn(true);

        UserResponseDTO response = avatarService.delete();

        verify(avatarRepository).deleteById(5L);
        assertNull(camila.getAvatarUpdatedAt());
        assertNull(response.getAvatarUrl());
    }

    // TEST 4: el administrador tampoco puede quitar su foto
    @Test
    void delete_admin_deberiaLanzarForbidden() {
        when(currentUserService.getCurrentUser()).thenReturn(user(1L, Role.ADMIN));

        assertThrows(ForbiddenOperationException.class, () -> avatarService.delete());
    }

    // TEST 5: la foto de un usuario se devuelve como JPEG
    @Test
    void find_conFoto_deberiaDevolverElJpeg() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.FREELANCER)));
        when(avatarRepository.findById(5L)).thenReturn(Optional.of(new UserAvatar(5L, new byte[]{7})));

        AvatarService.AvatarImage avatar = avatarService.find(5L);

        assertArrayEquals(new byte[]{7}, avatar.bytes());
        assertEquals("image/jpeg", avatar.contentType());
    }

    // TEST 6: el administrador siempre tiene la foto fija de DEVUTEC (PNG)
    @Test
    void find_admin_deberiaDevolverLaFotoFija() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.ADMIN)));

        AvatarService.AvatarImage avatar = avatarService.find(1L);

        assertEquals("image/png", avatar.contentType());
        assertTrue(avatar.bytes().length > 0);
        verifyNoInteractions(avatarRepository);
    }

    // TEST 7: sin foto, 404 (la web muestra las iniciales)
    @Test
    void find_sinFoto_deberiaLanzarNotFound() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.FREELANCER)));
        when(avatarRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> avatarService.find(5L));
    }

    // TEST 8: usuario que no existe, 404
    @Test
    void find_usuarioInexistente_deberiaLanzarNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> avatarService.find(99L));
    }
}
