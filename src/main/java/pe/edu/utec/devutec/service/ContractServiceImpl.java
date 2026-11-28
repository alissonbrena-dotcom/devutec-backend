package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.moderation.ContactGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.dto.ChangeRequestDTO;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.dto.DeliveryRequestDTO;
import pe.edu.utec.devutec.events.ContractCreatedEvent;
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
import pe.edu.utec.devutec.model.contract.ContractStage;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.model.contract.StageStatus;
import pe.edu.utec.devutec.payments.CheckoutService;
import pe.edu.utec.devutec.repository.ContractRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// El trabajo avanza por etapas: el freelancer entrega la etapa actual y el cliente la aprueba o pide cambios.
// Al aprobar la última etapa se confirma el contrato y se libera el pago.
// El estado del contrato acompaña a la etapa actual: DELIVERED mientras el cliente revisa, IN_PROGRESS si no.
// Un contrato sin etapas (anterior a esta funcionalidad) funciona como una sola entrega.
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;
    private final ContactGuard contactGuard;
    private final ContractMapper contractMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CheckoutService checkoutService;

    @Override
    @Transactional
    public void createFromApplication(Application application) {
        Payment payment = new Payment();
        payment.setAmount(application.getProposedPrice());

        Contract contract = new Contract();
        contract.setApplication(application);
        contract.setPayment(payment);
        // Los montos de cada etapa salen del precio aceptado (la contraoferta, si hubo) y las fechas, del plazo acordado
        LocalDate deadline = application.getProposedDeadline() != null
                ? application.getProposedDeadline()
                : application.getProject().getDeadline();
        ContractStagePlan.addDefaultStages(contract, application.getProposedPrice(), deadline);
        // save asigna el id al mismo objeto, así que contract.getId() ya sirve para el link del correo
        contractRepository.save(contract);

        Project project = application.getProject();
        eventPublisher.publishEvent(new ContractCreatedEvent(
                this, project.getClientId(), application.getFreelancer().getUser().getNombre(),
                project.getTitle(), payment.getAmount(), contract.getId()));
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

    // El freelancer entrega la etapa actual (o el trabajo completo si el contrato no tiene etapas)
    @Override
    @Transactional
    public ContractResponseDTO deliver(Long id, DeliveryRequestDTO request) {
        Contract contract = findContract(id);
        ensureIsFreelancer(contract);
        ensureStatus(contract, ContractStatus.IN_PROGRESS);
        // En el contrato se bloquea la intención de salirse (sobre todo pagar por fuera), no los datos
        contactGuard.checkInContract(currentUserService.getCurrentUser(), "Contrato " + id + " (entrega)", request.nota());

        // El link se valida antes de cambiar nada (agrega https:// si falta y revisa que tenga dominio)
        String link = SocialLinks.link(request.link(), "El link de entrega no parece válido, por ejemplo drive.google.com/...");
        if (link == null) {
            throw new InvalidOperationException("Pega el link de tu trabajo");
        }
        String note = request.nota() == null || request.nota().isBlank() ? null : request.nota().trim();
        LocalDateTime now = LocalDateTime.now();

        Optional<ContractStage> current = ContractStagePlan.currentStage(contract);
        current.ifPresent(stage -> {
            stage.setStatus(StageStatus.DELIVERED);
            stage.setDeliveredAt(now);
            stage.setDeliveryUrl(link);
            stage.setDeliveryNote(note);
        });

        contract.setStatus(ContractStatus.DELIVERED);
        contract.setDeliveredAt(now);
        contract.setDeliveryUrl(link);
        contract.setDeliveryNote(note);

        Application application = contract.getApplication();
        String title = current.filter(stage -> contract.getStages().size() > 1)
                .map(stage -> application.getProject().getTitle() + " · " + stage.getName())
                .orElse(application.getProject().getTitle());
        eventPublisher.publishEvent(new ContractDeliveredEvent(
                this,
                application.getProject().getClientId(),
                title,
                application.getFreelancer().getUser().getNombre(),
                contract.getId(),
                link,
                note));
        return contractMapper.toResponse(contract);
    }

    // El cliente aprueba la etapa entregada. Si era la última, se confirma el contrato y se libera el pago;
    // si no, empieza la siguiente etapa.
    @Override
    @Transactional
    public ContractResponseDTO confirm(Long id) {
        Contract contract = findContract(id);
        ensureIsClient(contract);
        ensureStatus(contract, ContractStatus.DELIVERED);

        Optional<ContractStage> current = ContractStagePlan.currentStage(contract);
        boolean lastStage = current.map(stage -> ContractStagePlan.isLastStage(contract, stage)).orElse(true);
        if (lastStage) {
            checkoutService.ensurePaidBeforeRelease(contract);
        }

        current.ifPresent(stage -> {
            stage.setStatus(StageStatus.APPROVED);
            stage.setApprovedAt(LocalDateTime.now());
        });

        if (lastStage) {
            contract.setStatus(ContractStatus.CONFIRMED);
            contract.getApplication().getProject().setStatus(ProjectStatus.DONE);
            paymentService.releasePayment(contract.getPayment().getId());
        } else {
            ContractStagePlan.currentStage(contract).ifPresent(next -> next.setStatus(StageStatus.IN_PROGRESS));
            startNextStage(contract);
        }
        return contractMapper.toResponse(contract);
    }

    // El cliente pide cambios en la etapa entregada: vuelve al freelancer. Máximo 2 rondas por etapa.
    @Override
    @Transactional
    public ContractResponseDTO requestChanges(Long id, ChangeRequestDTO request) {
        Contract contract = findContract(id);
        ensureIsClient(contract);
        ensureStatus(contract, ContractStatus.DELIVERED);
        contactGuard.checkInContract(currentUserService.getCurrentUser(), "Contrato " + id + " (pedido de cambios)",
                request.nota());

        ContractStage stage = ContractStagePlan.currentStage(contract)
                .orElseThrow(() -> new InvalidOperationException("Este contrato no tiene etapas para pedir cambios"));
        if (stage.getChangeRounds() >= ContractStage.MAX_CHANGE_ROUNDS) {
            throw new InvalidOperationException("Ya pediste " + ContractStage.MAX_CHANGE_ROUNDS
                    + " rondas de cambios en la etapa " + stage.getName() + ". Revísala y apruébala para seguir.");
        }

        stage.setStatus(StageStatus.CHANGES_REQUESTED);
        stage.setChangeRounds(stage.getChangeRounds() + 1);
        stage.setChangeRequestNote(request.nota().trim());
        startNextStage(contract);
        return contractMapper.toResponse(contract);
    }

    @Override
    @Transactional
    public ContractResponseDTO cancel(Long id) {
        Contract contract = findContract(id);
        ensureIsClient(contract);
        ensureStatus(contract, ContractStatus.IN_PROGRESS);
        // Si ya aprobó etapas, cancelar devolvería también lo que el estudiante ya entregó y el cliente aceptó.
        // Por eso, si el cliente no hace el pago 2, el contrato queda en pausa (decisión del equipo): no hay forma de
        // cerrarlo a la mitad ni recordatorios automáticos por ahora. Si hay un problema, lo ve soporte.
        if (contract.getStages().stream().anyMatch(stage -> stage.getStatus() == StageStatus.APPROVED)) {
            throw new InvalidOperationException(
                    "No puedes cancelar: ya aprobaste etapas de este contrato. Si hay un problema, escríbenos a soporte.");
        }
        checkoutService.refundIfPaid(contract);

        contract.getStages().stream()
                .filter(stage -> stage.getStatus() != StageStatus.APPROVED)
                .forEach(stage -> stage.setStatus(StageStatus.CANCELLED));
        contract.setStatus(ContractStatus.CANCELLED);
        contract.getApplication().getProject().setStatus(ProjectStatus.OPEN);
        paymentService.refundPayment(contract.getPayment().getId());
        return contractMapper.toResponse(contract);
    }

    // El contrato vuelve a "en curso" y se limpia la entrega anterior, que queda guardada en su etapa
    private void startNextStage(Contract contract) {
        contract.setStatus(ContractStatus.IN_PROGRESS);
        contract.setDeliveredAt(null);
        contract.setDeliveryUrl(null);
        contract.setDeliveryNote(null);
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
