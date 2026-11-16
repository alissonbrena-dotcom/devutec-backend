package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.SocialLinksDTO;
import pe.edu.utec.devutec.dto.UserResponseDTO;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.UnauthorizedException;
import pe.edu.utec.devutec.mapper.UserMapper;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserRepository userRepository;

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

    // TEST - El celular se guarda limpio (solo los 9 dígitos) y vuelve en la respuesta
    @Test
    void updatePhone_formatoConEspacios_deberiaGuardarLos9Digitos() {
        User camila = buildUser(20L, "Camila Ríos", "camila@test.com", Role.FREELANCER);
        when(currentUserService.getCurrentUser()).thenReturn(camila);
        when(userRepository.findById(20L)).thenReturn(Optional.of(camila));

        UserResponseDTO response = userService.updatePhone("+51 987 654 321");

        assertEquals("987654321", camila.getCelular());
        assertEquals("987654321", response.getCelular());
    }

    // TEST - Un celular inválido no cambia nada
    @Test
    void updatePhone_invalido_noDeberiaGuardarNada() {
        assertThrows(ApiException.class, () -> userService.updatePhone("12345"));
        verify(userRepository, never()).findById(20L);
    }

    // TEST - Quitar el celular lo deja en null
    @Test
    void deletePhone_conCelular_deberiaDejarloEnNull() {
        User cliente = buildUser(10L, "Café Aula 3", "cliente@test.com", Role.CLIENT);
        cliente.setCelular("987654321");
        when(currentUserService.getCurrentUser()).thenReturn(cliente);
        when(userRepository.findById(10L)).thenReturn(Optional.of(cliente));

        UserResponseDTO response = userService.deletePhone();

        assertNull(cliente.getCelular());
        assertNull(response.getCelular());
    }

    // TEST - Las redes se guardan como links completos y lo vacío se quita
    @Test
    void updateLinks_usuarioYWeb_deberiaGuardarLinksCompletos() {
        User cliente = buildUser(10L, "Pizzería Don Mario", "cliente@test.com", Role.CLIENT);
        cliente.setFacebook("https://facebook.com/vieja");
        when(currentUserService.getCurrentUser()).thenReturn(cliente);
        when(userRepository.findById(10L)).thenReturn(Optional.of(cliente));

        UserResponseDTO response = userService.updateLinks(new SocialLinksDTO("@pizzeriadonmario", "", "@donmario", "pizzeriadonmario.pe"));

        assertEquals("https://instagram.com/pizzeriadonmario", cliente.getInstagram());
        assertNull(cliente.getFacebook());
        assertEquals("https://tiktok.com/@donmario", cliente.getTiktok());
        assertEquals("https://pizzeriadonmario.pe", response.getRedes().sitioWeb());
    }

    // TEST - Si una red es inválida no se guarda ninguna
    @Test
    void updateLinks_unaInvalida_noDeberiaGuardarNada() {
        SocialLinksDTO links = new SocialLinksDTO("@pizzeria", "https://instagram.com/pizzeria", null, null);

        assertThrows(ApiException.class, () -> userService.updateLinks(links));
        verify(userRepository, never()).findById(10L);
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
