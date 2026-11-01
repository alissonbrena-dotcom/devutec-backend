package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.FreelancerProfileResponseDTO;
import pe.edu.utec.devutec.dto.FreelancerProfileUpdateDTO;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.FreelancerProfileMapper;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FreelancerProfileServiceImplTest {

    @Mock
    private FreelancerProfileRepository freelancerProfileRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Spy
    private FreelancerProfileMapper freelancerProfileMapper = new FreelancerProfileMapper();

    @InjectMocks
    private FreelancerProfileServiceImpl freelancerProfileService;

    private User freelancer;
    private FreelancerProfile profile;

    @BeforeEach
    void setUp() {
        freelancer = new User();
        freelancer.setId(20L);
        freelancer.setNombre("Camila Ríos");
        freelancer.setEmail("camila@test.com");
        freelancer.setRol(Role.FREELANCER);

        profile = new FreelancerProfile();
        profile.setId(5L);
        profile.setUser(freelancer);
        profile.setUniversidad("UTEC");
        profile.setCiclo(6);
        profile.setEgresado(false);
        profile.setTarifaHora(new BigDecimal("35.00"));
        profile.setPortafolioUrl("https://camila.dev");
        profile.setGithubUsername("camila-rios");
    }

    // TEST 1 - El freelancer autenticado obtiene su perfil con su id de usuario y nombre
    @Test
    void getMyProfile_conPerfil_deberiaDevolverSusDatos() {
        // Given
        when(currentUserService.getCurrentUser()).thenReturn(freelancer);
        when(freelancerProfileRepository.findByUserId(20L)).thenReturn(Optional.of(profile));

        // When
        FreelancerProfileResponseDTO response = freelancerProfileService.getMyProfile();

        // Then
        assertEquals(20L, response.getUserId());
        assertEquals("Camila Ríos", response.getNombre());
        assertEquals("UTEC", response.getUniversidad());
        assertEquals(6, response.getCiclo());
        assertEquals("camila-rios", response.getGithubUsername());
    }

    // TEST 2 - Si el usuario autenticado no tiene perfil se lanza ResourceNotFoundException (404)
    @Test
    void getMyProfile_sinPerfil_deberiaLanzarNotFound() {
        // Given
        when(currentUserService.getCurrentUser()).thenReturn(freelancer);
        when(freelancerProfileRepository.findByUserId(20L)).thenReturn(Optional.empty());

        // When + Then
        assertThrows(ResourceNotFoundException.class, () -> freelancerProfileService.getMyProfile());
    }

    // TEST 3 - Solo se actualizan los campos enviados; los null se mantienen
    @Test
    void updateMyProfile_conCamposParciales_deberiaMantenerLosNoEnviados() {
        // Given
        mockMyProfileAndSave();
        FreelancerProfileUpdateDTO dto = new FreelancerProfileUpdateDTO();
        dto.setTarifaHora(new BigDecimal("50.00"));

        // When
        FreelancerProfileResponseDTO response = freelancerProfileService.updateMyProfile(dto);

        // Then
        assertEquals(new BigDecimal("50.00"), response.getTarifaHora());
        assertEquals("UTEC", response.getUniversidad());
        assertEquals(6, response.getCiclo());
        assertEquals("https://camila.dev", response.getPortafolioUrl());
        assertEquals("camila-rios", response.getGithubUsername());
        verify(freelancerProfileRepository).save(profile);
    }

    // TEST 4 - Se actualizan todos los campos enviados
    @Test
    void updateMyProfile_conTodosLosCampos_deberiaActualizarlos() {
        // Given
        mockMyProfileAndSave();
        FreelancerProfileUpdateDTO dto = new FreelancerProfileUpdateDTO(
                "PUCP", 8, null, new BigDecimal("45.00"), "https://nuevo.dev", "camila-dev");

        // When
        FreelancerProfileResponseDTO response = freelancerProfileService.updateMyProfile(dto);

        // Then
        assertEquals("PUCP", response.getUniversidad());
        assertEquals(8, response.getCiclo());
        assertEquals(new BigDecimal("45.00"), response.getTarifaHora());
        assertEquals("https://nuevo.dev", response.getPortafolioUrl());
        assertEquals("camila-dev", response.getGithubUsername());
    }

    // TEST 5 - Marcar egresado borra el ciclo
    @Test
    void updateMyProfile_marcandoEgresado_deberiaBorrarElCiclo() {
        // Given
        mockMyProfileAndSave();
        FreelancerProfileUpdateDTO dto = new FreelancerProfileUpdateDTO();
        dto.setEgresado(true);

        // When
        FreelancerProfileResponseDTO response = freelancerProfileService.updateMyProfile(dto);

        // Then
        assertTrue(response.getEgresado());
        assertNull(response.getCiclo());
    }

    // TEST 6 - Indicar un ciclo deja de marcar al freelancer como egresado
    @Test
    void updateMyProfile_conCiclo_deberiaDesmarcarEgresado() {
        // Given
        profile.setCiclo(null);
        profile.setEgresado(true);
        mockMyProfileAndSave();
        FreelancerProfileUpdateDTO dto = new FreelancerProfileUpdateDTO();
        dto.setCiclo(4);

        // When
        FreelancerProfileResponseDTO response = freelancerProfileService.updateMyProfile(dto);

        // Then
        assertEquals(4, response.getCiclo());
        assertFalse(response.getEgresado());
    }

    // TEST 7 - Actualizar sin tener perfil lanza ResourceNotFoundException y no guarda nada
    @Test
    void updateMyProfile_sinPerfil_deberiaLanzarNotFound() {
        // Given
        when(currentUserService.getCurrentUser()).thenReturn(freelancer);
        when(freelancerProfileRepository.findByUserId(20L)).thenReturn(Optional.empty());

        // When + Then
        assertThrows(ResourceNotFoundException.class,
                () -> freelancerProfileService.updateMyProfile(new FreelancerProfileUpdateDTO()));
        verify(freelancerProfileRepository, never()).save(any());
    }

    // TEST 8 - Cualquier usuario puede ver el perfil de un freelancer por su id de usuario
    @Test
    void findByUserId_conPerfil_deberiaDevolverlo() {
        // Given
        when(freelancerProfileRepository.findByUserId(20L)).thenReturn(Optional.of(profile));

        // When
        FreelancerProfileResponseDTO response = freelancerProfileService.findByUserId(20L);

        // Then
        assertEquals(20L, response.getUserId());
        assertEquals("Camila Ríos", response.getNombre());
        verifyNoInteractions(currentUserService);
    }

    // TEST 9 - Un id de usuario sin perfil (cliente o inexistente) lanza ResourceNotFoundException
    @Test
    void findByUserId_sinPerfil_deberiaLanzarNotFound() {
        // Given
        when(freelancerProfileRepository.findByUserId(999L)).thenReturn(Optional.empty());

        // When + Then
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> freelancerProfileService.findByUserId(999L));
        assertTrue(ex.getMessage().contains("999"));
    }

    private void mockMyProfileAndSave() {
        when(currentUserService.getCurrentUser()).thenReturn(freelancer);
        when(freelancerProfileRepository.findByUserId(20L)).thenReturn(Optional.of(profile));
        when(freelancerProfileRepository.save(any(FreelancerProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }
}
