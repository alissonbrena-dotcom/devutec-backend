package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.moderation.ContactGuard;
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
import pe.edu.utec.devutec.dto.ChangeRequestDTO;
import pe.edu.utec.devutec.dto.DeliveryRequestDTO;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.mapper.ContractMapper;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.ProjectStatus;
import pe.edu.utec.devutec.model.application.AppStatus;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStage;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.model.contract.StageStatus;
import pe.edu.utec.devutec.payments.CheckoutService;
import pe.edu.utec.devutec.repository.ContractRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

// Contratos por etapas: entregar, aprobar, pedir cambios y cancelar etapa por etapa
@ExtendWith(MockitoExtension.class)
class ContractStagesTest {

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

    @Mock
    private CheckoutService checkoutService;

    @Mock
    private ContactGuard contactGuard;

    @Mock
    private pe.edu.utec.devutec.payments.InstallmentService installmentService;

    @Mock
    private pe.edu.utec.devutec.payments.SecondPaymentService secondPaymentService;

    @InjectMocks
    private ContractServiceImpl contractService;

    private User client;
    private User freelancerUser;
    private Application application;
    private Project project;

    @BeforeEach
    void setUp() {
        client = new User();
        client.setId(1L);
        client.setRol(Role.CLIENT);
        freelancerUser = new User();
        freelancerUser.setId(2L);
        freelancerUser.setNombre("Sofía");
        freelancerUser.setRol(Role.FREELANCER);

        FreelancerProfile freelancer = new FreelancerProfile();
        freelancer.setId(10L);
        freelancer.setUser(freelancerUser);

        project = new Project();
        project.setId(100L);
        project.setTitle("Web para pizzería");
        project.setClientId(client.getId());
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setDeadline(LocalDate.now().plusDays(40));

        application = new Application();
        application.setId(1L);
        application.setProject(project);
        application.setFreelancer(freelancer);
        application.setProposedPrice(new BigDecimal("1200"));
        application.setStatus(AppStatus.ACCEPTED);
    }

    // TEST 1 - Un contrato nuevo tiene 4 etapas con sus montos, y lo que sobra es la bolsa de emergencia
    @Test
    void createFromApplication_deberiaCrearCuatroEtapasConSusMontos() {
        contractService.createFromApplication(application);

        Contract contract = savedContract();
        List<ContractStage> stages = contract.getStages();
        assertEquals(List.of("Descubrimiento", "Diseño", "Desarrollo", "Entrega final"),
                stages.stream().map(ContractStage::getName).toList());
        assertEquals(new BigDecimal("120.00"), stages.get(0).getAmount());
        assertEquals(new BigDecimal("360.00"), stages.get(1).getAmount());
        assertEquals(new BigDecimal("480.00"), stages.get(2).getAmount());
        assertEquals(new BigDecimal("120.00"), stages.get(3).getAmount());
        assertEquals(new BigDecimal("120.00"), ContractStagePlan.emergencyFund(contract, new BigDecimal("1200")));
        assertEquals(StageStatus.IN_PROGRESS, stages.get(0).getStatus());
        assertEquals(StageStatus.PENDING, stages.get(1).getStatus());
        assertEquals(List.of(1, 1, 2, 2), stages.stream().map(ContractStage::getPaymentPart).toList());
    }

    // TEST 2 - Con contraoferta de monto y plazo, las etapas usan el precio y la fecha que propuso el freelancer
    @Test
    void createFromApplication_conContraoferta_deberiaRecalcularMontosYFechas() {
        LocalDate proposed = LocalDate.now().plusDays(20);
        application.setProposedPrice(new BigDecimal("1000"));
        application.setProposedDeadline(proposed);

        contractService.createFromApplication(application);

        List<ContractStage> stages = savedContract().getStages();
        assertEquals(new BigDecimal("400.00"), stages.get(2).getAmount());
        assertEquals(proposed, stages.get(3).getDueDate());
        assertTrue(stages.get(0).getDueDate().isBefore(stages.get(1).getDueDate()));
    }

    // TEST 3 - Entregar marca entregada solo la etapa actual
    @Test
    void deliver_deberiaEntregarLaEtapaActual() {
        Contract contract = contractWithStages();
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(freelancerUser);

        contractService.deliver(1L, new DeliveryRequestDTO("figma.com/file/pizzeria", "Te dejo el diseño"));

        assertEquals(ContractStatus.DELIVERED, contract.getStatus());
        assertEquals(StageStatus.DELIVERED, contract.getStages().get(0).getStatus());
        assertEquals("https://figma.com/file/pizzeria", contract.getStages().get(0).getDeliveryUrl());
        assertEquals(StageStatus.PENDING, contract.getStages().get(1).getStatus());
    }

    // TEST 4 - Aprobar una etapa que no es la última pasa a la siguiente sin liberar el pago
    @Test
    void confirm_etapaIntermedia_deberiaEmpezarLaSiguienteSinLiberarPago() {
        Contract contract = contractWithStages();
        deliverCurrent(contract);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        contractService.confirm(1L);

        assertEquals(StageStatus.APPROVED, contract.getStages().get(0).getStatus());
        assertNotNull(contract.getStages().get(0).getApprovedAt());
        assertEquals(StageStatus.IN_PROGRESS, contract.getStages().get(1).getStatus());
        assertEquals(ContractStatus.IN_PROGRESS, contract.getStatus());
        assertNull(contract.getDeliveryUrl());
        verify(paymentService, never()).releasePayment(anyLong());
        verify(checkoutService, never()).ensurePaidBeforeRelease(any());
    }

    // TEST 4b - Aprobar el Diseño (última etapa del pago 1) arranca el plazo del pago 2; aprobar Descubrimiento no
    @Test
    void confirm_diseno_deberiaArrancarElPlazoDelPago2() {
        Contract contract = contractWithStages();
        contract.getStages().get(0).setStatus(StageStatus.APPROVED);
        contract.getStages().get(1).setStatus(StageStatus.IN_PROGRESS);
        deliverCurrent(contract);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(installmentService.hasPlan(1L)).thenReturn(true);

        contractService.confirm(1L);

        assertEquals(StageStatus.IN_PROGRESS, contract.getStages().get(2).getStatus());
        verify(secondPaymentService).open(contract);
    }

    @Test
    void confirm_descubrimiento_noDeberiaArrancarElPlazoDelPago2() {
        Contract contract = contractWithStages();
        deliverCurrent(contract);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        lenient().when(installmentService.hasPlan(1L)).thenReturn(true);

        contractService.confirm(1L);

        verify(secondPaymentService, never()).open(any());
    }

    // TEST 5 - Aprobar la última etapa confirma el contrato y libera el pago
    @Test
    void confirm_ultimaEtapa_deberiaConfirmarContratoYLiberarPago() {
        Contract contract = contractWithStages();
        contract.getStages().forEach(stage -> stage.setStatus(StageStatus.APPROVED));
        contract.getStages().get(3).setStatus(StageStatus.IN_PROGRESS);
        deliverCurrent(contract);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        contractService.confirm(1L);

        assertEquals(ContractStatus.CONFIRMED, contract.getStatus());
        assertEquals(ProjectStatus.DONE, project.getStatus());
        assertEquals(StageStatus.APPROVED, contract.getStages().get(3).getStatus());
        verify(checkoutService).ensurePaidBeforeRelease(contract);
        verify(paymentService).releasePayment(50L);
    }

    // TEST 6 - Pedir cambios devuelve la etapa al freelancer y suma una ronda
    @Test
    void requestChanges_deberiaDevolverLaEtapaAlFreelancer() {
        Contract contract = contractWithStages();
        deliverCurrent(contract);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        contractService.requestChanges(1L, new ChangeRequestDTO("  Cambia el color del logo  "));

        ContractStage stage = contract.getStages().get(0);
        assertEquals(StageStatus.CHANGES_REQUESTED, stage.getStatus());
        assertEquals(1, stage.getChangeRounds());
        assertEquals("Cambia el color del logo", stage.getChangeRequestNote());
        assertEquals(ContractStatus.IN_PROGRESS, contract.getStatus());
    }

    // TEST 7 - Después de 2 rondas de cambios ya no se pueden pedir más en esa etapa
    @Test
    void requestChanges_terceraRonda_deberiaLanzarSinCambiarNada() {
        Contract contract = contractWithStages();
        deliverCurrent(contract);
        contract.getStages().get(0).setChangeRounds(2);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        assertThrows(InvalidOperationException.class,
                () -> contractService.requestChanges(1L, new ChangeRequestDTO("Otro cambio más")));
        assertEquals(StageStatus.DELIVERED, contract.getStages().get(0).getStatus());
        assertEquals(ContractStatus.DELIVERED, contract.getStatus());
    }

    // TEST 8 - Solo se pueden pedir cambios sobre una etapa entregada
    @Test
    void requestChanges_sinEntrega_deberiaLanzar() {
        Contract contract = contractWithStages();
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        assertThrows(InvalidOperationException.class,
                () -> contractService.requestChanges(1L, new ChangeRequestDTO("Cambia algo")));
    }

    // TEST 9 - Al cancelar antes de aprobar etapas, todas quedan canceladas y se devuelve el pago
    @Test
    void cancel_sinEtapasAprobadas_deberiaCancelarTodasLasEtapas() {
        Contract contract = contractWithStages();
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        contractService.cancel(1L);

        assertEquals(ContractStatus.CANCELLED, contract.getStatus());
        assertTrue(contract.getStages().stream().allMatch(stage -> stage.getStatus() == StageStatus.CANCELLED));
        verify(paymentService).refundPayment(50L);
    }

    // TEST 9b - Con etapas ya aprobadas no se puede cancelar: el estudiante perdería lo que ya entregó
    @Test
    void cancel_conEtapasAprobadas_deberiaLanzarSinReembolsar() {
        Contract contract = contractWithStages();
        contract.getStages().get(0).setStatus(StageStatus.APPROVED);
        contract.getStages().get(1).setStatus(StageStatus.IN_PROGRESS);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        assertThrows(InvalidOperationException.class, () -> contractService.cancel(1L));

        assertEquals(ContractStatus.IN_PROGRESS, contract.getStatus());
        assertEquals(StageStatus.IN_PROGRESS, contract.getStages().get(1).getStatus());
        verify(checkoutService, never()).refundIfPaid(any());
        verify(paymentService, never()).refundPayment(anyLong());
    }

    // TEST 9c - El monto liberado es la suma de las etapas aprobadas
    @Test
    void releasedAmount_deberiaSumarLasEtapasAprobadas() {
        Contract contract = contractWithStages();
        contract.getStages().get(0).setStatus(StageStatus.APPROVED);
        contract.getStages().get(1).setStatus(StageStatus.APPROVED);

        assertEquals(new BigDecimal("480.00"), ContractStagePlan.releasedAmount(contract));
    }

    // TEST 10 - Un contrato anterior a las etapas recibe una sola etapa del 100 % con su mismo estado
    @Test
    void addSingleStage_contratoEntregado_deberiaCrearEtapaEntregada() {
        Contract contract = baseContract();
        contract.setStatus(ContractStatus.DELIVERED);
        contract.setDeliveryUrl("https://github.com/sofia/web");

        ContractStagePlan.addSingleStage(contract);

        assertEquals(1, contract.getStages().size());
        ContractStage stage = contract.getStages().get(0);
        assertEquals("Trabajo completo", stage.getName());
        assertEquals(100, stage.getPercentage());
        assertEquals(new BigDecimal("1200.00"), stage.getAmount());
        assertEquals(StageStatus.DELIVERED, stage.getStatus());
        assertEquals("https://github.com/sofia/web", stage.getDeliveryUrl());
        assertEquals(BigDecimal.ZERO.setScale(2), ContractStagePlan.emergencyFund(contract, new BigDecimal("1200.00")));
    }

    private Contract savedContract() {
        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).save(captor.capture());
        return captor.getValue();
    }

    private Contract baseContract() {
        Payment payment = new Payment();
        payment.setId(50L);
        payment.setAmount(new BigDecimal("1200"));
        payment.setStatus(PaymentStatus.HELD);

        Contract contract = new Contract();
        contract.setId(1L);
        contract.setApplication(application);
        contract.setPayment(payment);
        contract.setStatus(ContractStatus.IN_PROGRESS);
        return contract;
    }

    private Contract contractWithStages() {
        Contract contract = baseContract();
        ContractStagePlan.addDefaultStages(contract, new BigDecimal("1200"), project.getDeadline());
        return contract;
    }

    private void deliverCurrent(Contract contract) {
        ContractStagePlan.currentStage(contract).orElseThrow().setStatus(StageStatus.DELIVERED);
        contract.setStatus(ContractStatus.DELIVERED);
    }
}
