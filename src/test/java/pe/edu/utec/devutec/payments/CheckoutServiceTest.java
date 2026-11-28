package pe.edu.utec.devutec.payments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.CheckoutResponseDTO;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.mapper.ContractMapper;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ContractMapper contractMapper;

    @Mock
    private StripeGateway stripeGateway;

    @Mock

    private pe.edu.utec.devutec.payments.InstallmentService installmentService;


    @InjectMocks
    private CheckoutService checkoutService;

    private User client;
    private User otherUser;
    private Contract contract;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(checkoutService, "frontendUrl", "https://devutec.vercel.app");

        client = new User();
        client.setId(1L);
        client.setRol(Role.CLIENT);
        otherUser = new User();
        otherUser.setId(99L);
        otherUser.setRol(Role.CLIENT);

        Project project = new Project();
        project.setId(100L);
        project.setTitle("Web para restaurante");
        project.setClientId(client.getId());

        FreelancerProfile freelancer = new FreelancerProfile();
        freelancer.setUser(new User());

        Application application = new Application();
        application.setProject(project);
        application.setFreelancer(freelancer);

        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("1200.50"));
        payment.setStatus(PaymentStatus.HELD);

        contract = new Contract();
        contract.setId(5L);
        contract.setApplication(application);
        contract.setPayment(payment);
        contract.setStatus(ContractStatus.IN_PROGRESS);
    }

    // TEST 1 - El cliente pide pagar y recibe el link de Stripe, con el monto en céntimos
    @Test
    void startCheckout_porElCliente_deberiaCrearSesionConElMontoEnCentimos() {
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeCheckoutSession("cs_test_1", "https://checkout.stripe.com/c/pay/cs_test_1", "unpaid", null, "5"));

        CheckoutResponseDTO response = checkoutService.startCheckout(5L);

        assertEquals("cs_test_1", response.sessionId());
        assertEquals("https://checkout.stripe.com/c/pay/cs_test_1", response.checkoutUrl());

        ArgumentCaptor<StripeGateway.CheckoutRequest> captor = ArgumentCaptor.forClass(StripeGateway.CheckoutRequest.class);
        verify(stripeGateway).createCheckoutSession(captor.capture());
        StripeGateway.CheckoutRequest request = captor.getValue();
        assertEquals(120050L, request.amountInCents());
        assertEquals("pen", request.currency());
        assertEquals("5", request.contractId());
        assertTrue(request.successUrl().startsWith("https://devutec.vercel.app/contracts/5?pago=ok"));
        assertTrue(request.successUrl().contains("{CHECKOUT_SESSION_ID}"));
    }

    // TEST 2 - Otra persona no puede pagar el contrato
    @Test
    void startCheckout_porOtroUsuario_deberiaLanzarForbidden() {
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(otherUser);

        assertThrows(ForbiddenOperationException.class, () -> checkoutService.startCheckout(5L));
        verifyNoInteractions(stripeGateway);
    }

    // TEST 3 - Un contrato ya pagado no se vuelve a cobrar
    @Test
    void startCheckout_contratoYaPagado_deberiaLanzarExcepcion() {
        contract.getPayment().setStripeReference("pi_test_1");
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        assertThrows(InvalidOperationException.class, () -> checkoutService.startCheckout(5L));
        verifyNoInteractions(stripeGateway);
    }

    // TEST 4 - Un contrato cancelado no se puede pagar
    @Test
    void startCheckout_contratoCancelado_deberiaLanzarExcepcion() {
        contract.setStatus(ContractStatus.CANCELLED);
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        assertThrows(InvalidOperationException.class, () -> checkoutService.startCheckout(5L));
    }

    // TEST 5 - Al volver de Stripe con el pago hecho, se guarda el comprobante
    @Test
    void confirmCheckout_pagado_deberiaGuardarLaReferenciaDeStripe() {
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(stripeGateway.getCheckoutSession("cs_test_1"))
                .thenReturn(new StripeCheckoutSession("cs_test_1", null, "paid", "pi_test_1", "5"));

        checkoutService.confirmCheckout(5L, "cs_test_1");

        assertEquals("pi_test_1", contract.getPayment().getStripeReference());
        assertEquals(PaymentStatus.HELD, contract.getPayment().getStatus());
        verify(contractMapper).toResponse(contract);
    }

    // TEST 6 - Si Stripe dice que no se pagó, no se guarda nada
    @Test
    void confirmCheckout_sinPagar_deberiaLanzarExcepcion() {
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(stripeGateway.getCheckoutSession("cs_test_1"))
                .thenReturn(new StripeCheckoutSession("cs_test_1", null, "unpaid", null, "5"));

        assertThrows(InvalidOperationException.class, () -> checkoutService.confirmCheckout(5L, "cs_test_1"));
        assertNull(contract.getPayment().getStripeReference());
    }

    // TEST 7 - Una sesión de otro contrato no sirve para confirmar este
    @Test
    void confirmCheckout_sesionDeOtroContrato_deberiaLanzarExcepcion() {
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(stripeGateway.getCheckoutSession("cs_test_9"))
                .thenReturn(new StripeCheckoutSession("cs_test_9", null, "paid", "pi_test_9", "9"));

        assertThrows(InvalidOperationException.class, () -> checkoutService.confirmCheckout(5L, "cs_test_9"));
        assertNull(contract.getPayment().getStripeReference());
    }

    // TEST 8 - Confirmar dos veces no vuelve a llamar a Stripe
    @Test
    void confirmCheckout_yaConfirmado_noDeberiaLlamarAStripe() {
        contract.getPayment().setStripeReference("pi_test_1");
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        checkoutService.confirmCheckout(5L, "cs_test_1");

        verifyNoInteractions(stripeGateway);
        verify(contractMapper).toResponse(contract);
    }

    // TEST 8b - Si el freelancer ya entregó, el cliente todavía puede pagar (para luego confirmar)
    @Test
    void startCheckout_contratoEntregado_deberiaPermitirPagar() {
        contract.setStatus(ContractStatus.DELIVERED);
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeCheckoutSession("cs_test_2", "https://checkout.stripe.com/y", "unpaid", null, "5"));

        assertEquals("cs_test_2", checkoutService.startCheckout(5L).sessionId());
    }

    // TEST 9 - Un monto menor al mínimo de Stripe se rechaza con un mensaje claro
    @Test
    void startCheckout_montoMenorAlMinimo_deberiaLanzarExcepcion() {
        contract.getPayment().setAmount(new BigDecimal("1.50"));
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);

        InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> checkoutService.startCheckout(5L));
        assertTrue(ex.getMessage().contains("S/ 2.00"));
        verifyNoInteractions(stripeGateway);
    }

    // TEST 10 - El nombre del producto en Stripe usa la marca DEVUTEC
    // Desde la app: Stripe vuelve a la página puente /pago-listo, que abre la app con el link que mandó
    @Test
    void startCheckout_desdeLaApp_deberiaVolverALaPaginaPuenteConElLinkDeLaApp() {
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeCheckoutSession("cs_test_1", "https://checkout.stripe.com/x", "unpaid", null, "5"));

        checkoutService.startCheckout(5L, "devutec://contracts/5");

        ArgumentCaptor<StripeGateway.CheckoutRequest> captor = ArgumentCaptor.forClass(StripeGateway.CheckoutRequest.class);
        verify(stripeGateway).createCheckoutSession(captor.capture());
        StripeGateway.CheckoutRequest request = captor.getValue();
        assertEquals("https://devutec.vercel.app/pago-listo?volver=devutec%3A%2F%2Fcontracts%2F5&estado=ok", request.successUrl());
        assertEquals("https://devutec.vercel.app/pago-listo?volver=devutec%3A%2F%2Fcontracts%2F5&estado=cancelado", request.cancelUrl());
    }

    // Sin link de la app (la web) se mantiene el regreso al detalle del contrato
    @Test
    void startCheckout_sinLinkDeLaApp_deberiaVolverAlDetalleDelContrato() {
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeCheckoutSession("cs_test_1", "https://checkout.stripe.com/x", "unpaid", null, "5"));

        checkoutService.startCheckout(5L, "  ");

        ArgumentCaptor<StripeGateway.CheckoutRequest> captor = ArgumentCaptor.forClass(StripeGateway.CheckoutRequest.class);
        verify(stripeGateway).createCheckoutSession(captor.capture());
        assertEquals("https://devutec.vercel.app/contracts/5?pago=cancelado", captor.getValue().cancelUrl());
    }

    @Test
    void startCheckout_deberiaUsarLaMarcaDevutec() {
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeCheckoutSession("cs_test_1", "https://checkout.stripe.com/x", "unpaid", null, "5"));

        checkoutService.startCheckout(5L);

        ArgumentCaptor<StripeGateway.CheckoutRequest> captor = ArgumentCaptor.forClass(StripeGateway.CheckoutRequest.class);
        verify(stripeGateway).createCheckoutSession(captor.capture());
        assertEquals("DEVUTEC - Web para restaurante", captor.getValue().productName());
    }

    // TEST 11 - Con el interruptor apagado, se puede confirmar sin haber pagado (como hasta ahora)
    @Test
    void ensurePaidBeforeRelease_interruptorApagado_noDeberiaExigirPago() {
        ReflectionTestUtils.setField(checkoutService, "requirePayment", false);

        assertDoesNotThrow(() -> checkoutService.ensurePaidBeforeRelease(contract));
    }

    // TEST 12 - Con el interruptor prendido, no se libera un contrato sin pagar
    @Test
    void ensurePaidBeforeRelease_interruptorPrendidoSinPagar_deberiaLanzarExcepcion() {
        ReflectionTestUtils.setField(checkoutService, "requirePayment", true);

        assertThrows(InvalidOperationException.class, () -> checkoutService.ensurePaidBeforeRelease(contract));
    }

    // TEST 13 - Con el interruptor prendido y el contrato pagado, se puede liberar
    @Test
    void ensurePaidBeforeRelease_interruptorPrendidoYPagado_noDeberiaLanzar() {
        ReflectionTestUtils.setField(checkoutService, "requirePayment", true);
        contract.getPayment().setStripeReference("pi_test_1");

        assertDoesNotThrow(() -> checkoutService.ensurePaidBeforeRelease(contract));
    }

    // TEST 14 - Al cancelar un contrato pagado con tarjeta, se pide el reembolso a Stripe
    @Test
    void refundIfPaid_pagadoConTarjeta_deberiaPedirReembolso() {
        contract.getPayment().setStripeReference("pi_test_1");

        checkoutService.refundIfPaid(contract);

        verify(stripeGateway).refund("pi_test_1");
    }

    // TEST 15 - Si no se pagó con tarjeta, no hay nada que devolver en Stripe
    @Test
    void refundIfPaid_sinPagoConTarjeta_noDeberiaLlamarAStripe() {
        checkoutService.refundIfPaid(contract);

        verifyNoInteractions(stripeGateway);
    }

    // TEST 16 - Conversión a céntimos
    @Test
    void toCents_deberiaMultiplicarPorCien() {
        assertEquals(120000L, CheckoutService.toCents(new BigDecimal("1200")));
        assertEquals(1999L, CheckoutService.toCents(new BigDecimal("19.99")));
    }

    // Pago en dos partes: se cobra solo la cuota, con referencia "contrato-cuota"
    @Test
    void startCheckout_conPlanDeDosPagos_deberiaCobrarSoloLaCuota() {
        pe.edu.utec.devutec.model.PaymentInstallment cuota = new pe.edu.utec.devutec.model.PaymentInstallment();
        cuota.setNumber(1);
        cuota.setLabel("Pago 1");
        cuota.setClientAmount(new BigDecimal("600.25"));
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(installmentService.hasPlan(5L)).thenReturn(true);
        when(installmentService.payable(contract, null)).thenReturn(cuota);
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeCheckoutSession("cs_test_2", "https://checkout.stripe.com/c/pay/cs_test_2", "unpaid", null, "5-1"));

        checkoutService.startCheckout(5L, null, null);

        ArgumentCaptor<StripeGateway.CheckoutRequest> captor = ArgumentCaptor.forClass(StripeGateway.CheckoutRequest.class);
        verify(stripeGateway).createCheckoutSession(captor.capture());
        assertEquals(60025L, captor.getValue().amountInCents());
        assertEquals("5-1", captor.getValue().contractId());
    }

    // Pago en dos partes: al volver de Stripe se marca pagada la cuota correcta
    @Test
    void confirmCheckout_conPlanDeDosPagos_deberiaMarcarLaCuota() {
        pe.edu.utec.devutec.model.PaymentInstallment cuota = new pe.edu.utec.devutec.model.PaymentInstallment();
        cuota.setNumber(2);
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(installmentService.hasPlan(5L)).thenReturn(true);
        when(stripeGateway.getCheckoutSession("cs_test_3"))
                .thenReturn(new StripeCheckoutSession("cs_test_3", null, "paid", "pi_test_3", "5-2"));
        when(installmentService.find(5L, 2)).thenReturn(cuota);

        checkoutService.confirmCheckout(5L, "cs_test_3");

        verify(installmentService).markPaid(cuota, "pi_test_3");
    }

    // Pago en dos partes: una sesión de otro contrato no sirve
    @Test
    void confirmCheckout_conPlanYReferenciaDeOtroContrato_deberiaLanzarExcepcion() {
        when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(installmentService.hasPlan(5L)).thenReturn(true);
        when(stripeGateway.getCheckoutSession("cs_x"))
                .thenReturn(new StripeCheckoutSession("cs_x", null, "paid", "pi_x", "55-1"));

        assertThrows(InvalidOperationException.class, () -> checkoutService.confirmCheckout(5L, "cs_x"));
        verify(installmentService, never()).markPaid(any(), any());
    }
}
