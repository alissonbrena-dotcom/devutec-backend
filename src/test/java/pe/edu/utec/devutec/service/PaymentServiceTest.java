package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.modelmapper.ModelMapper;
import pe.edu.utec.devutec.dto.PaymentRequestDTO;
import pe.edu.utec.devutec.dto.PaymentResponseDTO;
import pe.edu.utec.devutec.events.PaymentReleasedEvent;
import pe.edu.utec.devutec.exceptions.ConflictException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository repository;

    @Mock
    private ApplicationEventPublisher publisher;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private PaymentService paymentService;

    // TEST 1 - Crear un pago debería guardar el pago con su monto
    @Test
    void createPayment_deberiaGuardarPagoConMonto() {
        // Given (preparar)
        BigDecimal monto = new BigDecimal("100.00");
        PaymentRequestDTO request = new PaymentRequestDTO();
        request.setAmount(monto);

        Payment pagoMapeado = new Payment();
        pagoMapeado.setAmount(monto);

        Payment pagoGuardado = new Payment();
        pagoGuardado.setId(1L);
        pagoGuardado.setAmount(monto);
        pagoGuardado.setStatus(PaymentStatus.HELD);

        when(modelMapper.map(request, Payment.class)).thenReturn(pagoMapeado);
        when(repository.save(any(Payment.class))).thenReturn(pagoGuardado);
        when(modelMapper.map(any(Payment.class), eq(PaymentResponseDTO.class)))
                .thenReturn(new PaymentResponseDTO());

        // When (ejecutar)
        paymentService.createPayment(request);

        // Then (verificar)
        verify(repository, times(1)).save(any(Payment.class));
    }

    // TEST 2 - Liberar un pago en HELD lo pasa a RELEASED
    @Test
    void releasePayment_conPagoHeld_deberiaLiberarlo() {
        // Given
        Payment pago = new Payment();
        pago.setId(1L);
        pago.setAmount(new BigDecimal("100.00"));
        pago.setStatus(PaymentStatus.HELD);

        when(repository.findById(1L)).thenReturn(Optional.of(pago));
        when(repository.save(any(Payment.class))).thenReturn(pago);
        when(modelMapper.map(any(Payment.class), eq(PaymentResponseDTO.class)))
                .thenReturn(new PaymentResponseDTO());

        // When
        paymentService.releasePayment(1L);

        // Then
        assertEquals(PaymentStatus.RELEASED, pago.getStatus());
        verify(publisher, times(1)).publishEvent(any(PaymentReleasedEvent.class));
    }

    // TEST 3 - Liberar un pago que NO está HELD lanza ConflictException
    @Test
    void releasePayment_conPagoNoHeld_deberiaLanzarConflict() {
        // Given
        Payment pago = new Payment();
        pago.setId(1L);
        pago.setStatus(PaymentStatus.RELEASED); // ya está liberado

        when(repository.findById(1L)).thenReturn(Optional.of(pago));

        // When + Then
        assertThrows(ConflictException.class, () -> paymentService.releasePayment(1L));
    }

    // TEST 4 - Buscar un pago inexistente lanza ResourceNotFoundException
    @Test
    void findById_conIdInexistente_deberiaLanzarNotFound() {
        // Given
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // When + Then
        assertThrows(ResourceNotFoundException.class, () -> paymentService.findById(999L));
    }
}
