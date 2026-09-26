package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.events.ContractDeliveredEvent;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.ContractMapper;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.ProjectStatus;
import pe.edu.utec.devutec.model.application.AppStatus;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.repository.ContractRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ContractMapper contractMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ContractServiceImpl contractService;

    private User client;
    private User freelancerUser;
    private Application application;
    private Project project;

    @BeforeEach
    void setUp() {
        client = createUser(1L, "Cliente Uno", Role.CLIENT);
        freelancerUser = createUser(2L, "Free Uno", Role.FREELANCER);

        FreelancerProfile freelancer = new FreelancerProfile();
        freelancer.setId(10L);
        freelancer.setUser(freelancerUser);

        project = new Project();
        project.setId(100L);
        project.setTitle("Web para restaurante");
        project.setClientId(client.getId());
        project.setStatus(ProjectStatus.IN_PROGRESS);

        application = new Application();
        application.setId(1L);
        application.setProject(project);
        application.setFreelancer(freelancer);
        application.setProposedPrice(new BigDecimal("1200"));
        application.setStatus(AppStatus.ACCEPTED);
    }

    // TEST 1 - Al crear el contrato, el pago queda retenido por el precio propuesto
    @Test
    void createFromApplication_deberiaCrearContratoConPagoPorElPrecioPropuesto() {
        // When
        contractService.createFromApplication(application);

        // Then
        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).save(captor.capture());
        Contract contract = captor.getValue();
        assertEquals(application, contract.getApplication());
        assertNotNull(contract.getPayment());
        assertEquals(new BigDecimal("1200"), contract.getPayment().getAmount());
    }

    // TEST 2 - El freelancer entrega el trabajo
    @Test
    void deliver_porElFreelancer_deberiaMarcarEntregadoYPublicarEvento() {
        // Given
        Contract contract = createContract(ContractStatus.IN_PROGRESS);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(freelancerUser);

        // When
        contractService.deliver(1L);

        // Then
        assertEquals(ContractStatus.DELIVERED, contract.getStatus());
        assertNotNull(contract.getDeliveredAt());
        verify(eventPublisher).publishEvent(any(ContractDeliveredEvent.class));
    }

    // TEST 3 - El cliente no puede marcar el trabajo como entregado
    @Test
    void deliver_porElCliente_deberiaLanzarForbidden() {
        // Given
        Contract contract = createContract(ContractStatus.IN_PROGRESS);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        // When / Then
        assertThrows(ForbiddenOperationException.class, () -> contractService.deliver(1L));
        assertEquals(ContractStatus.IN_PROGRESS, contract.getStatus());
    }

    // TEST 4 - No se puede entregar dos veces
    @Test
    void deliver_contratoYaEntregado_deberiaLanzarInvalidOperation() {
        // Given
        Contract contract = createContract(ContractStatus.DELIVERED);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(freelancerUser);

        // When / Then
        assertThrows(InvalidOperationException.class, () -> contractService.deliver(1L));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // TEST 5 - Al confirmar: contrato CONFIRMED, pago liberado y proyecto DONE
    @Test
    void confirm_porElCliente_deberiaConfirmarLiberarPagoYCerrarProyecto() {
        // Given
        Contract contract = createContract(ContractStatus.DELIVERED);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        // When
        contractService.confirm(1L);

        // Then
        assertEquals(ContractStatus.CONFIRMED, contract.getStatus());
        assertEquals(ProjectStatus.DONE, project.getStatus());
        verify(paymentService).releasePayment(50L);
    }

    // TEST 6 - No se puede confirmar si el freelancer aún no entregó
    @Test
    void confirm_sinEntrega_deberiaLanzarInvalidOperationSinLiberarPago() {
        // Given
        Contract contract = createContract(ContractStatus.IN_PROGRESS);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        // When / Then
        assertThrows(InvalidOperationException.class, () -> contractService.confirm(1L));
        verify(paymentService, never()).releasePayment(anyLong());
    }

    // TEST 7 - Al cancelar: contrato CANCELLED, pago reembolsado y proyecto vuelve a OPEN
    @Test
    void cancel_porElCliente_deberiaCancelarReembolsarYReabrirProyecto() {
        // Given
        Contract contract = createContract(ContractStatus.IN_PROGRESS);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        // When
        contractService.cancel(1L);

        // Then
        assertEquals(ContractStatus.CANCELLED, contract.getStatus());
        assertEquals(ProjectStatus.OPEN, project.getStatus());
        verify(paymentService).refundPayment(50L);
    }

    // TEST 8 - Otro cliente no puede cancelar un contrato ajeno
    @Test
    void cancel_porOtroCliente_deberiaLanzarForbiddenSinReembolsar() {
        // Given
        Contract contract = createContract(ContractStatus.IN_PROGRESS);
        User otherClient = createUser(3L, "Otro Cliente", Role.CLIENT);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(otherClient);

        // When / Then
        assertThrows(ForbiddenOperationException.class, () -> contractService.cancel(1L));
        verify(paymentService, never()).refundPayment(anyLong());
    }

    // TEST 9 - "Mis contratos" de un freelancer usa la consulta por freelancer
    @Test
    void findMine_comoFreelancer_deberiaBuscarPorFreelancer() {
        // Given
        when(currentUserService.getCurrentUser()).thenReturn(freelancerUser);
        when(contractRepository.findByApplication_Freelancer_User_Id(2L)).thenReturn(List.of(createContract(ContractStatus.IN_PROGRESS)));

        // When
        int total = contractService.findMine().size();

        // Then
        assertEquals(1, total);
        verify(contractRepository, never()).findByApplication_Project_ClientId(anyLong());
    }

    // TEST 10 - "Mis contratos" de un cliente usa la consulta por cliente
    @Test
    void findMine_comoCliente_deberiaBuscarPorCliente() {
        // Given
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(contractRepository.findByApplication_Project_ClientId(1L)).thenReturn(List.of());

        // When
        contractService.findMine();

        // Then
        verify(contractRepository).findByApplication_Project_ClientId(1L);
        verify(contractRepository, never()).findByApplication_Freelancer_User_Id(anyLong());
    }

    @Test
    void findById_contratoInexistente_deberiaLanzarNotFound() {
        when(contractRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> contractService.findById(99L));
    }

    @Test
    void confirm_porElFreelancer_deberiaLanzarForbiddenSinLiberarPago() {
        Contract contract = createContract(ContractStatus.DELIVERED);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(freelancerUser);

        assertThrows(ForbiddenOperationException.class, () -> contractService.confirm(1L));
        assertEquals(ContractStatus.DELIVERED, contract.getStatus());
        verify(paymentService, never()).releasePayment(anyLong());
    }

    private User createUser(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setNombre(name);
        user.setEmail(name.toLowerCase().replace(" ", "") + "@test.com");
        user.setRol(role);
        return user;
    }

    private Contract createContract(ContractStatus status) {
        Payment payment = new Payment();
        payment.setId(50L);
        payment.setAmount(new BigDecimal("1200"));
        payment.setStatus(PaymentStatus.HELD);

        Contract contract = new Contract();
        contract.setId(1L);
        contract.setApplication(application);
        contract.setPayment(payment);
        contract.setStatus(status);
        return contract;
    }
}
