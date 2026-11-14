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

    // TEST 9 - Conversión a céntimos
    @Test
    void toCents_deberiaMultiplicarPorCien() {
        assertEquals(120000L, CheckoutService.toCents(new BigDecimal("1200")));
        assertEquals(1999L, CheckoutService.toCents(new BigDecimal("19.99")));
    }
}
