package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.modelmapper.ModelMapper;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.dto.PaymentResponseDTO;
import pe.edu.utec.devutec.events.PaymentReleasedEvent;
import pe.edu.utec.devutec.exceptions.InvalidPaymentStateException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.mapper.ContractMapper;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.repository.ContractRepository;
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

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ContractMapper contractMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    // TEST 1 - Reembolsar un pago en HELD lo pasa a REFUNDED sin publicar evento de liberación
    @Test
    void refundPayment_conPagoHeld_deberiaReembolsarlo() {
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
        paymentService.refundPayment(1L);

        // Then
        assertEquals(PaymentStatus.REFUNDED, pago.getStatus());
        verify(publisher, never()).publishEvent(any(PaymentReleasedEvent.class));
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

    // TEST 3 - Liberar un pago que NO está HELD lanza InvalidPaymentStateException indicando el estado actual
    @Test
    void releasePayment_conPagoNoHeld_deberiaLanzarConflict() {
        // Given
        Payment pago = new Payment();
        pago.setId(1L);
        pago.setStatus(PaymentStatus.RELEASED); // ya está liberado

        when(repository.findById(1L)).thenReturn(Optional.of(pago));

        // When + Then
        InvalidPaymentStateException ex = assertThrows(InvalidPaymentStateException.class,
                () -> paymentService.releasePayment(1L));
        assertTrue(ex.getMessage().contains("RELEASED"));
    }

    // TEST 4 - Buscar un pago inexistente lanza ResourceNotFoundException
    @Test
    void findById_conIdInexistente_deberiaLanzarNotFound() {
        // Given
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // When + Then
        assertThrows(ResourceNotFoundException.class, () -> paymentService.findById(999L));
    }

    // TEST 5 - El pago trae el contrato, el proyecto y las personas a las que pertenece
    @Test
    void findById_conContrato_deberiaIncluirSusDatos() {
        // Given
        Payment pago = new Payment();
        pago.setId(7L);
        pago.setStatus(PaymentStatus.HELD);
        Contract contrato = new Contract();

        ContractResponseDTO datos = new ContractResponseDTO();
        datos.setId(42L);
        datos.setProjectTitle("Web para pizzería");
        datos.setClientName("Pizzería Don Mario");
        datos.setFreelancerName("Camila Ríos");

        when(repository.findById(7L)).thenReturn(Optional.of(pago));
        when(modelMapper.map(any(Payment.class), eq(PaymentResponseDTO.class))).thenReturn(new PaymentResponseDTO());
        when(contractRepository.findByPayment_Id(7L)).thenReturn(Optional.of(contrato));
        when(contractMapper.toResponse(contrato)).thenReturn(datos);

        // When
        PaymentResponseDTO respuesta = paymentService.findById(7L);

        // Then
        assertEquals(42L, respuesta.getContractId());
        assertEquals("Web para pizzería", respuesta.getProjectTitle());
        assertEquals("Pizzería Don Mario", respuesta.getClientName());
        assertEquals("Camila Ríos", respuesta.getFreelancerName());
    }
}
