package pe.edu.utec.devutec.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utec.devutec.auth.domain.FreelancerProfile;
import pe.edu.utec.devutec.repository.FreelancerProfileRepository;
import pe.edu.utec.devutec.repository.ReviewRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FreelancerRatingUpdaterTest {

    private static final Long FREELANCER_USER_ID = 20L;

    @Mock
    private FreelancerProfileRepository freelancerProfileRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private FreelancerRatingUpdater updater;

    // TEST 1 - Reseñas de 5 y 4 estrellas dejan el promedio en 4.50
    @Test
    void onReviewCreated_freelancer_deberiaGuardarPromedioConDosDecimales() {
        // Given
        FreelancerProfile profile = new FreelancerProfile();
        when(freelancerProfileRepository.findByUserId(FREELANCER_USER_ID)).thenReturn(Optional.of(profile));
        when(reviewRepository.averageRatingByRevieweeId(FREELANCER_USER_ID)).thenReturn(4.5);

        // When
        updater.onReviewCreated(eventFor(FREELANCER_USER_ID));

        // Then
        assertEquals(new BigDecimal("4.50"), profile.getCalificacionPromedio());
        verify(freelancerProfileRepository).save(profile);
    }

    // TEST 2 - Un promedio periódico (5, 4, 4) se redondea a 4.33
    @Test
    void onReviewCreated_promedioPeriodico_deberiaRedondearADosDecimales() {
        // Given
        FreelancerProfile profile = new FreelancerProfile();
        when(freelancerProfileRepository.findByUserId(FREELANCER_USER_ID)).thenReturn(Optional.of(profile));
        when(reviewRepository.averageRatingByRevieweeId(FREELANCER_USER_ID)).thenReturn(13.0 / 3);

        // When
        updater.onReviewCreated(eventFor(FREELANCER_USER_ID));

        // Then
        assertEquals(new BigDecimal("4.33"), profile.getCalificacionPromedio());
    }

    // TEST 3 - Si el reseñado es un cliente (sin perfil de freelancer) no se actualiza nada
    @Test
    void onReviewCreated_cliente_noDeberiaActualizarNada() {
        // Given
        when(freelancerProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());

        // When
        updater.onReviewCreated(eventFor(10L));

        // Then
        verify(reviewRepository, never()).averageRatingByRevieweeId(any());
        verify(freelancerProfileRepository, never()).save(any());
    }

    private static ReviewCreatedEvent eventFor(Long revieweeId) {
        return new ReviewCreatedEvent(new Object(), 1L, revieweeId, "user@test.com",
                "Usuario", "Autor", "Landing page", 5);
    }
}
