package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.UserResponseDTO;
import pe.edu.utec.devutec.exceptions.UnauthorizedException;
import pe.edu.utec.devutec.mapper.UserMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private CurrentUserService currentUserService;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @InjectMocks
    private UserServiceImpl userService;

    // TEST 1 - Devuelve los datos del cliente autenticado con el rol como texto
    @Test
    void getCurrentUser_cliente_deberiaDevolverSusDatos() {
        // Given
        when(currentUserService.getCurrentUser()).thenReturn(buildUser(10L, "Café Aula 3", "cliente@test.com", Role.CLIENT));

        // When
        UserResponseDTO response = userService.getCurrentUser();

        // Then
        assertEquals(10L, response.getId());
        assertEquals("Café Aula 3", response.getNombre());
        assertEquals("cliente@test.com", response.getEmail());
        assertEquals("CLIENT", response.getRol());
    }

    // TEST 2 - El rol del freelancer se devuelve correctamente
    @Test
    void getCurrentUser_freelancer_deberiaDevolverRolFreelancer() {
        // Given
        when(currentUserService.getCurrentUser()).thenReturn(buildUser(20L, "Camila Ríos", "camila@test.com", Role.FREELANCER));

        // When
        UserResponseDTO response = userService.getCurrentUser();

        // Then
        assertEquals(20L, response.getId());
        assertEquals("FREELANCER", response.getRol());
    }

    // TEST 3 - Sin sesión se propaga UnauthorizedException (la API responde 401)
    @Test
    void getCurrentUser_sinSesion_deberiaLanzarUnauthorized() {
        // Given
        when(currentUserService.getCurrentUser())
                .thenThrow(new UnauthorizedException("Debes iniciar sesión para realizar esta acción"));

        // When + Then
        assertThrows(UnauthorizedException.class, () -> userService.getCurrentUser());
    }

    private static User buildUser(Long id, String nombre, String email, Role rol) {
        User user = new User();
        user.setId(id);
        user.setNombre(nombre);
        user.setEmail(email);
        user.setPassword("$2a$10$hashDePrueba");
        user.setRol(rol);
        return user;
    }
}
