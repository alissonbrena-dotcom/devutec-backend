package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.events.ContractDeliveredEvent;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.ContractMapper;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.ProjectStatus;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.repository.ContractRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;
    private final ContractMapper contractMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void createFromApplication(Application application) {
        Payment payment = new Payment();
        payment.setAmount(application.getProposedPrice());

        Contract contract = new Contract();
        contract.setApplication(application);
        contract.setPayment(payment);
        contractRepository.save(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractResponseDTO> findMine() {
        User user = currentUserService.getCurrentUser();
        List<Contract> contracts = user.getRol() == Role.FREELANCER
                ? contractRepository.findByApplication_Freelancer_User_Id(user.getId())
                : contractRepository.findByApplication_Project_ClientId(user.getId());
        return contracts.stream()
                .map(contractMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ContractResponseDTO findById(Long id) {
        Contract contract = findContract(id);
        ensureIsParticipant(contract);
        return contractMapper.toResponse(contract);
    }

    @Override
    @Transactional
    public ContractResponseDTO deliver(Long id) {
        Contract contract = findContract(id);
        ensureIsFreelancer(contract);
        ensureStatus(contract, ContractStatus.IN_PROGRESS);

        contract.setStatus(ContractStatus.DELIVERED);
        contract.setDeliveredAt(LocalDateTime.now());

        Application application = contract.getApplication();
        eventPublisher.publishEvent(new ContractDeliveredEvent(
                this,
                application.getProject().getClientId(),
                application.getProject().getTitle(),
                application.getFreelancer().getUser().getNombre()));
        return contractMapper.toResponse(contract);
    }

    @Override
    @Transactional
    public ContractResponseDTO confirm(Long id) {
        Contract contract = findContract(id);
        ensureIsClient(contract);
        ensureStatus(contract, ContractStatus.DELIVERED);

        contract.setStatus(ContractStatus.CONFIRMED);
        contract.getApplication().getProject().setStatus(ProjectStatus.DONE);
        paymentService.releasePayment(contract.getPayment().getId());
        return contractMapper.toResponse(contract);
    }

    @Override
    @Transactional
    public ContractResponseDTO cancel(Long id) {
        Contract contract = findContract(id);
        ensureIsClient(contract);
        ensureStatus(contract, ContractStatus.IN_PROGRESS);

        contract.setStatus(ContractStatus.CANCELLED);
        contract.getApplication().getProject().setStatus(ProjectStatus.OPEN);
        paymentService.refundPayment(contract.getPayment().getId());
        return contractMapper.toResponse(contract);
    }

    private Contract findContract(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado con id: " + id));
    }

    private void ensureStatus(Contract contract, ContractStatus expected) {
        if (contract.getStatus() != expected) {
            throw new InvalidOperationException(
                    "El contrato debe estar en estado " + expected + " para realizar esta acción");
        }
    }

    private void ensureIsClient(Contract contract) {
        if (!isClient(contract, currentUserService.getCurrentUser())) {
            throw new ForbiddenOperationException("Solo el cliente del proyecto puede realizar esta acción");
        }
    }

    private void ensureIsFreelancer(Contract contract) {
        if (!isFreelancer(contract, currentUserService.getCurrentUser())) {
            throw new ForbiddenOperationException("Solo el freelancer del contrato puede realizar esta acción");
        }
    }

    private void ensureIsParticipant(Contract contract) {
        User user = currentUserService.getCurrentUser();
        if (!isClient(contract, user) && !isFreelancer(contract, user)) {
            throw new ForbiddenOperationException("No tienes acceso a este contrato");
        }
    }

    private boolean isClient(Contract contract, User user) {
        Project project = contract.getApplication().getProject();
        return user.getId().equals(project.getClientId());
    }

    private boolean isFreelancer(Contract contract, User user) {
        return user.getId().equals(contract.getApplication().getFreelancer().getUser().getId());
    }
}