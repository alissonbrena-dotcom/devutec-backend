package pe.edu.utec.devutec.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

// Los celulares del contrato son para el botón "Escribir por WhatsApp" entre las dos personas
@ExtendWith(MockitoExtension.class)
class ContractMapperTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ContractMapper contractMapper;

    private User client;
    private User freelancerUser;

    @BeforeEach
    void setUp() {
        client = user(10L, "Pizzería Don Mario", Role.CLIENT, "912345678");
        freelancerUser = user(20L, "Camila Ríos", Role.FREELANCER, "987654321");
        when(userRepository.findById(10L)).thenReturn(Optional.of(client));
    }

    private static User user(Long id, String nombre, Role rol, String celular) {
        User user = new User();
        user.setId(id);
        user.setNombre(nombre);
        user.setRol(rol);
        user.setCelular(celular);
        return user;
    }

    private Contract contract(ContractStatus status) {
        FreelancerProfile freelancer = new FreelancerProfile();
        freelancer.setUser(freelancerUser);
        Project project = new Project();
        project.setId(5L);
        project.setTitle("Web para pizzería");
        project.setClientId(client.getId());
        Application application = new Application();
        application.setProject(project);
        application.setFreelancer(freelancer);
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("600"));
        payment.setStatus(PaymentStatus.HELD);
        Contract contract = new Contract();
        contract.setId(21L);
        contract.setApplication(application);
        contract.setPayment(payment);
        contract.setStatus(status);
        return contract;
    }

    // TEST 1: en un contrato en curso se comparten los dos celulares
    @Test
    void toResponse_contratoEnCurso_deberiaIncluirLosCelulares() {
        ContractResponseDTO response = contractMapper.toResponse(contract(ContractStatus.IN_PROGRESS));

        assertEquals("912345678", response.getClientPhone());
        assertEquals("987654321", response.getFreelancerPhone());
        assertEquals("Pizzería Don Mario", response.getClientName());
    }

    // TEST 2: también en uno terminado, por si hay que coordinar algo después de la entrega
    @Test
    void toResponse_contratoConfirmado_deberiaIncluirLosCelulares() {
        ContractResponseDTO response = contractMapper.toResponse(contract(ContractStatus.CONFIRMED));

        assertNotNull(response.getClientPhone());
        assertNotNull(response.getFreelancerPhone());
    }

    // TEST 3: con el contrato cancelado ya no se comparten
    @Test
    void toResponse_contratoCancelado_noDeberiaIncluirLosCelulares() {
        ContractResponseDTO response = contractMapper.toResponse(contract(ContractStatus.CANCELLED));

        assertNull(response.getClientPhone());
        assertNull(response.getFreelancerPhone());
        assertEquals("Pizzería Don Mario", response.getClientName());
    }

    // TEST 4: si alguien no agregó su celular, llega null (la app muestra que todavía no lo agregó)
    @Test
    void toResponse_sinCelular_deberiaLlegarNull() {
        freelancerUser.setCelular(null);

        ContractResponseDTO response = contractMapper.toResponse(contract(ContractStatus.IN_PROGRESS));

        assertNull(response.getFreelancerPhone());
        assertEquals("912345678", response.getClientPhone());
    }
}
