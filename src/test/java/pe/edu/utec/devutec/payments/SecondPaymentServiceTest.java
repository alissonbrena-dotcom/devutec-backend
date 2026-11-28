package pe.edu.utec.devutec.payments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import pe.edu.utec.devutec.events.ContractClosedForNonPaymentEvent;
import pe.edu.utec.devutec.events.SecondPaymentReminderEvent;
import pe.edu.utec.devutec.model.InstallmentStatus;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentInstallment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStage;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.model.contract.StageStatus;
import pe.edu.utec.devutec.repository.PaymentInstallmentRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pago 2: plazo al aprobar el Diseño, recordatorios sin repetirse y cierre automático si no se paga.
// Contrato de S/ 880 (S/ 800 para el estudiante): pago 1 de S/ 440 (S/ 400 del estudiante, con S/ 80 de bolsa)
// ya pagado, con Descubrimiento y Diseño aprobados (S/ 320 liberados); pago 2 de S/ 440 pendiente.
class SecondPaymentServiceTest {

    private static final ZoneId LIMA = ZoneOffset.ofHours(-5);
    private static final LocalDateTime APPROVED = LocalDateTime.of(2026, 11, 28, 10, 0);

    private PaymentInstallmentRepository repository;
    private StripeGateway stripe;
    private ApplicationEventPublisher publisher;
    private InstallmentService installmentService;
    private Contract contract;
    private PaymentInstallment first;
    private PaymentInstallment second;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentInstallmentRepository.class);
        stripe = mock(StripeGateway.class);
        publisher = mock(ApplicationEventPublisher.class);
        installmentService = new InstallmentService(repository, stripe, new BigDecimal("0.10"));

        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("880.00"));
        payment.setStatus(PaymentStatus.HELD);
        contract = new Contract();
        contract.setId(5L);
        contract.setPayment(payment);
        contract.setStatus(ContractStatus.IN_PROGRESS);
        contract.addStage(stage(1, "Descubrimiento", 1, StageStatus.APPROVED));
        contract.addStage(stage(2, "Diseño", 1, StageStatus.APPROVED));
        contract.addStage(stage(3, "Desarrollo", 2, StageStatus.IN_PROGRESS));
        contract.addStage(stage(4, "Entrega final", 2, StageStatus.PENDING));

        first = installment(1L, 1, "440.00", "400.00", "80.00", InstallmentStatus.HELD);
        first.setReleasedAmount(new BigDecimal("320.00"));
        first.setStripeReference("pi_pago1");
        second = installment(2L, 2, "440.00", "400.00", "0", InstallmentStatus.PENDING);

        when(repository.findByContract_IdOrderByNumberAsc(5L)).thenReturn(List.of(first, second));
        when(repository.findById(2L)).thenReturn(Optional.of(second));
    }

    private SecondPaymentService serviceAt(LocalDateTime now, Duration term) {
        Clock clock = Clock.fixed(now.atZone(LIMA).toInstant(), LIMA);
        return new SecondPaymentService(repository, installmentService, publisher, term, clock);
    }

    private SecondPaymentService serviceAt(LocalDateTime now) {
        return serviceAt(now, Duration.ofDays(7));
    }

    private static ContractStage stage(int position, String name, int part, StageStatus status) {
        ContractStage stage = new ContractStage();
        stage.setPosition(position);
        stage.setName(name);
        stage.setPaymentPart(part);
        stage.setStatus(status);
        return stage;
    }

    private PaymentInstallment installment(Long id, int number, String client, String student, String reserve,
                                           InstallmentStatus status) {
        PaymentInstallment installment = new PaymentInstallment();
        installment.setId(id);
        installment.setContract(contract);
        installment.setNumber(number);
        installment.setLabel("Pago " + number);
        installment.setClientAmount(new BigDecimal(client));
        installment.setStudentAmount(new BigDecimal(student));
        installment.setCommissionAmount(new BigDecimal(client).subtract(new BigDecimal(student)));
        installment.setReserveAmount(new BigDecimal(reserve));
        installment.setStatus(status);
        return installment;
    }

    private List<SecondPaymentReminderEvent> reminders() {
        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(publisher, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(SecondPaymentReminderEvent.class::isInstance)
                .map(SecondPaymentReminderEvent.class::cast)
                .toList();
    }

    @Test
    void alAprobarElDisenoCorreElPlazoYSeMandaElPrimerAviso() {
        serviceAt(APPROVED).open(contract);

        assertEquals(APPROVED.plusDays(7), second.getDueAt());
        assertEquals(1, second.getRemindersSent());
        assertEquals(1, reminders().size());
        assertEquals(1, reminders().get(0).getReminderNumber());
        assertEquals(new BigDecimal("440.00"), reminders().get(0).getAmount());
    }

    @Test
    void abrirDosVecesNoReiniciaElPlazoNiRepiteElAviso() {
        serviceAt(APPROVED).open(contract);
        serviceAt(APPROVED.plusDays(2)).open(contract);

        assertEquals(APPROVED.plusDays(7), second.getDueAt());
        assertEquals(1, reminders().size());
    }

    @Test
    void recordatorioAMitadDelPlazoYUnDiaAntesSinRepetirse() {
        serviceAt(APPROVED).open(contract);

        assertEquals(SecondPaymentService.Outcome.NOTHING, serviceAt(APPROVED.plusDays(2)).process(2L));
        assertEquals(SecondPaymentService.Outcome.REMINDER_SENT, serviceAt(APPROVED.plusHours(84)).process(2L));
        assertEquals(SecondPaymentService.Outcome.NOTHING, serviceAt(APPROVED.plusHours(90)).process(2L));
        assertEquals(SecondPaymentService.Outcome.REMINDER_SENT, serviceAt(APPROVED.plusDays(6)).process(2L));
        assertEquals(SecondPaymentService.Outcome.NOTHING, serviceAt(APPROVED.plusDays(6).plusHours(2)).process(2L));

        assertEquals(List.of(1, 2, 3), reminders().stream().map(SecondPaymentReminderEvent::getReminderNumber).toList());
        assertEquals(ContractStatus.IN_PROGRESS, contract.getStatus());
    }

    @Test
    void siElJobCorreTardeMandaSoloElUltimoAviso() {
        serviceAt(APPROVED).open(contract);

        serviceAt(APPROVED.plusDays(6).plusHours(12)).process(2L);
        serviceAt(APPROVED.plusDays(6).plusHours(13)).process(2L);

        assertEquals(List.of(1, 3), reminders().stream().map(SecondPaymentReminderEvent::getReminderNumber).toList());
    }

    @Test
    void alVencerSinPagarSeCierraYLaBolsaVaAlEstudiante() {
        serviceAt(APPROVED).open(contract);

        assertEquals(SecondPaymentService.Outcome.CONTRACT_CLOSED, serviceAt(APPROVED.plusDays(7)).process(2L));

        assertEquals(ContractStatus.CANCELLED, contract.getStatus());
        assertEquals(SecondPaymentService.CLOSED_FOR_NON_PAYMENT, contract.getClosedReason());
        assertEquals(APPROVED.plusDays(7), contract.getClosedAt());
        // Lo aprobado sigue aprobado; lo que faltaba se cancela
        assertEquals(List.of(StageStatus.APPROVED, StageStatus.APPROVED, StageStatus.CANCELLED, StageStatus.CANCELLED),
                contract.getStages().stream().map(ContractStage::getStatus).toList());
        // Bolsa al estudiante: 320 aprobados + 80 de bolsa = 400. No quedaba nada retenido para devolver.
        assertEquals(new BigDecimal("400.00"), first.getReleasedAmount());
        assertEquals(InstallmentStatus.RELEASED, first.getStatus());
        assertEquals(PaymentStatus.RELEASED, contract.getPayment().getStatus());
        verify(stripe, never()).refund(anyString(), anyLong());

        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(publisher, times(2)).publishEvent(captor.capture());
        ContractClosedForNonPaymentEvent closed = (ContractClosedForNonPaymentEvent) captor.getAllValues().get(1);
        assertEquals(new BigDecimal("400.00"), closed.getStudentReceived());
        assertEquals(new BigDecimal("80.00"), closed.getEmergencyFund());
        assertEquals(0, closed.getRefundedToClient().signum());
    }

    @Test
    void loQueQuedaRetenidoDelPago1VuelveAlCliente() {
        // Solo se liberaron 280 de los 320 (por ejemplo, un redondeo): quedan 40 del estudiante sin usar
        first.setReleasedAmount(new BigDecimal("280.00"));
        serviceAt(APPROVED).open(contract);

        serviceAt(APPROVED.plusDays(7)).process(2L);

        // El estudiante recibe 280 + 80 de bolsa; los 40 restantes vuelven al cliente con su comisión (40 × 1,10 = 44)
        assertEquals(new BigDecimal("360.00"), first.getReleasedAmount());
        assertEquals(new BigDecimal("44.00"), first.getRefundedAmount());
        assertEquals(InstallmentStatus.REFUNDED, first.getStatus());
        verify(stripe).refund("pi_pago1", 4400L);
    }

    @Test
    void noSeCierraSiElClienteYaPago() {
        serviceAt(APPROVED).open(contract);
        second.setStatus(InstallmentStatus.HELD);

        assertEquals(SecondPaymentService.Outcome.NOTHING, serviceAt(APPROVED.plusDays(8)).process(2L));
        assertEquals(ContractStatus.IN_PROGRESS, contract.getStatus());
        assertNull(contract.getClosedReason());
    }

    @Test
    void noTocaContratosYaCerradosOTerminados() {
        serviceAt(APPROVED).open(contract);
        contract.setStatus(ContractStatus.CANCELLED);

        assertEquals(SecondPaymentService.Outcome.NOTHING, serviceAt(APPROVED.plusDays(8)).process(2L));
        assertNull(contract.getClosedReason());
    }

    @Test
    void cerrarEsIdempotente() {
        serviceAt(APPROVED).open(contract);
        serviceAt(APPROVED.plusDays(7)).process(2L);

        assertEquals(SecondPaymentService.Outcome.NOTHING, serviceAt(APPROVED.plusDays(8)).process(2L));
        verify(publisher, times(2)).publishEvent(any(ApplicationEvent.class));
    }

    @Test
    void conUnPlazoCortoParaLaDemoLosAvisosSeAjustan() {
        Duration twoMinutes = Duration.ofMinutes(2);
        serviceAt(APPROVED, twoMinutes).open(contract);
        assertEquals(APPROVED.plusMinutes(2), second.getDueAt());

        assertEquals(SecondPaymentService.Outcome.REMINDER_SENT, serviceAt(APPROVED.plusMinutes(1), twoMinutes).process(2L));
        assertEquals(SecondPaymentService.Outcome.REMINDER_SENT, serviceAt(APPROVED.plusSeconds(90), twoMinutes).process(2L));
        assertEquals(SecondPaymentService.Outcome.CONTRACT_CLOSED, serviceAt(APPROVED.plusMinutes(2), twoMinutes).process(2L));
    }
}
