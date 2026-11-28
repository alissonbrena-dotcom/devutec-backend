package pe.edu.utec.devutec.payments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.model.InstallmentStatus;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentInstallment;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.repository.PaymentInstallmentRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallmentServiceTest {

    @Mock
    private PaymentInstallmentRepository repository;

    @Mock
    private StripeGateway stripeGateway;

    private InstallmentService service;
    private Contract contract;

    @BeforeEach
    void setUp() {
        service = new InstallmentService(repository, stripeGateway, new BigDecimal("0.10"));
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("880.00"));
        contract = new Contract();
        contract.setId(12L);
        contract.setPayment(payment);
        contract.setStatus(ContractStatus.IN_PROGRESS);
    }

    @Test
    void split_separaLoDelEstudianteYLaComision() {
        InstallmentService.Split split = service.split(new BigDecimal("880.00"));
        assertEquals(new BigDecimal("800.00"), split.student());
        assertEquals(new BigDecimal("80.00"), split.commission());
    }

    @Test
    void split_conMontosQueNoDanExacto_sumaElTotal() {
        InstallmentService.Split split = service.split(new BigDecimal("825.00"));
        assertEquals(new BigDecimal("750.00"), split.student());
        assertEquals(new BigDecimal("825.00"), split.student().add(split.commission()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPlan_armaDosPagosDeLaMitadConBolsaEnElPrimero() {
        when(repository.existsByContract_Id(12L)).thenReturn(false);
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<PaymentInstallment> plan = service.createPlan(contract);

        assertEquals(2, plan.size());
        PaymentInstallment first = plan.get(0);
        PaymentInstallment second = plan.get(1);
        assertEquals(new BigDecimal("440.00"), first.getClientAmount());
        assertEquals(new BigDecimal("400.00"), first.getStudentAmount());
        assertEquals(new BigDecimal("40.00"), first.getCommissionAmount());
        assertEquals(new BigDecimal("80.00"), first.getReserveAmount());
        assertEquals(new BigDecimal("440.00"), second.getClientAmount());
        assertEquals(BigDecimal.ZERO, second.getReserveAmount());
        assertEquals(InstallmentStatus.PENDING, first.getStatus());
    }

    @Test
    void createPlan_conMontoChico_esUnSoloPago() {
        contract.getPayment().setAmount(new BigDecimal("15.00"));
        when(repository.existsByContract_Id(12L)).thenReturn(false);
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<PaymentInstallment> plan = service.createPlan(contract);

        assertEquals(1, plan.size());
        assertEquals(new BigDecimal("15.00"), plan.get(0).getClientAmount());
    }

    @Test
    void payable_elSegundoPagoEsperaAlPrimero() {
        List<PaymentInstallment> plan = plan();
        when(repository.findByContract_IdOrderByNumberAsc(12L)).thenReturn(plan);

        assertThrows(InvalidOperationException.class, () -> service.payable(contract, 2));
        assertEquals(1, service.payable(contract, null).getNumber());
    }

    @Test
    void payable_conElPrimeroPagado_habilitaElSegundo() {
        List<PaymentInstallment> plan = plan();
        plan.get(0).setStatus(InstallmentStatus.HELD);
        when(repository.findByContract_IdOrderByNumberAsc(12L)).thenReturn(plan);

        assertEquals(2, service.payable(contract, null).getNumber());
    }

    @Test
    void payable_contratoCancelado_noSePuedePagar() {
        contract.setStatus(ContractStatus.CANCELLED);
        when(repository.findByContract_IdOrderByNumberAsc(12L)).thenReturn(plan());

        assertThrows(InvalidOperationException.class, () -> service.payable(contract, 1));
    }

    @Test
    void releaseForStage_liberaLaEtapaSinTocarLaBolsa() {
        List<PaymentInstallment> plan = plan();
        plan.get(0).setStatus(InstallmentStatus.HELD);
        when(repository.findByContract_IdOrderByNumberAsc(12L)).thenReturn(plan);

        // Descubrimiento = 20 % de S/ 800 = S/ 160
        BigDecimal released = service.releaseForStage(contract, new BigDecimal("0.20"));
        assertEquals(new BigDecimal("160.00"), released);
        assertEquals(new BigDecimal("160.00"), plan.get(0).getReleasedAmount());

        // Diseño = otro 20 %: en el pago 1 solo quedan S/ 160 fuera de la bolsa de S/ 80
        released = service.releaseForStage(contract, new BigDecimal("0.20"));
        assertEquals(new BigDecimal("160.00"), released);
        assertEquals(new BigDecimal("320.00"), plan.get(0).getReleasedAmount());
        assertEquals(new BigDecimal("80.00"), plan.get(0).heldAmount());
    }

    @Test
    void releaseAll_liberaTodoIncluidaLaBolsa() {
        List<PaymentInstallment> plan = plan();
        plan.get(0).setStatus(InstallmentStatus.HELD);
        plan.get(1).setStatus(InstallmentStatus.HELD);
        when(repository.findByContract_IdOrderByNumberAsc(12L)).thenReturn(plan);

        assertEquals(new BigDecimal("800.00"), service.releaseAll(contract));
        assertEquals(InstallmentStatus.RELEASED, plan.get(0).getStatus());
        assertEquals(InstallmentStatus.RELEASED, plan.get(1).getStatus());
    }

    @Test
    void refundUnreleased_sinTrabajoAprobado_devuelveTodoElPagoConComision() {
        List<PaymentInstallment> plan = plan();
        plan.get(0).setStatus(InstallmentStatus.HELD);
        plan.get(0).setStripeReference("pi_123");
        when(repository.findByContract_IdOrderByNumberAsc(12L)).thenReturn(plan);

        BigDecimal refunded = service.refundUnreleased(contract, false);

        assertEquals(new BigDecimal("440.00"), refunded);
        verify(stripeGateway).refund("pi_123", 44000L);
        assertEquals(InstallmentStatus.REFUNDED, plan.get(0).getStatus());
        // El pago 2 nunca se pagó: no se devuelve nada
        verify(stripeGateway, never()).refund(eq("pi_456"), anyLong());
    }

    @Test
    void refundUnreleased_conTrabajoAprobado_laBolsaVaAlEstudianteYElRestoVuelve() {
        List<PaymentInstallment> plan = plan();
        PaymentInstallment first = plan.get(0);
        first.setStatus(InstallmentStatus.HELD);
        first.setStripeReference("pi_123");
        first.setReleasedAmount(new BigDecimal("160.00")); // Descubrimiento aprobado
        when(repository.findByContract_IdOrderByNumberAsc(12L)).thenReturn(plan);

        BigDecimal refunded = service.refundUnreleased(contract, true);

        // Del pago 1 (S/ 400 del estudiante): S/ 160 aprobados + S/ 80 de bolsa = S/ 240 al estudiante.
        // Quedan S/ 160, que con su comisión son S/ 176 para el cliente.
        assertEquals(new BigDecimal("240.00"), first.getReleasedAmount());
        assertEquals(new BigDecimal("176.00"), refunded);
        verify(stripeGateway).refund("pi_123", 17600L);
    }

    @Test
    void markPaid_conElPago1_marcaElContratoComoPagado() {
        PaymentInstallment first = plan().get(0);
        when(repository.save(first)).thenReturn(first);

        service.markPaid(first, "pi_789");

        assertEquals(InstallmentStatus.HELD, first.getStatus());
        assertEquals("pi_789", contract.getPayment().getStripeReference());
    }

    private List<PaymentInstallment> plan() {
        PaymentInstallment first = installment(1, "440.00", "400.00", "40.00", "80.00");
        PaymentInstallment second = installment(2, "440.00", "400.00", "40.00", "0");
        return List.of(first, second);
    }

    private PaymentInstallment installment(int number, String client, String student, String commission, String reserve) {
        PaymentInstallment installment = new PaymentInstallment();
        installment.setContract(contract);
        installment.setNumber(number);
        installment.setLabel("Pago " + number);
        installment.setClientAmount(new BigDecimal(client));
        installment.setStudentAmount(new BigDecimal(student));
        installment.setCommissionAmount(new BigDecimal(commission));
        installment.setReserveAmount(new BigDecimal(reserve));
        return installment;
    }
}
