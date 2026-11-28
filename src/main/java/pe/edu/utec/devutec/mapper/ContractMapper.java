package pe.edu.utec.devutec.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.dto.ContractStageResponseDTO;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStage;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.service.ContractStagePlan;

import java.time.LocalDate;
import pe.edu.utec.devutec.dto.InstallmentResponseDTO;
import pe.edu.utec.devutec.model.InstallmentStatus;
import pe.edu.utec.devutec.payments.InstallmentService;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class ContractMapper {

    private final UserRepository userRepository;
    private final InstallmentService installmentService;

    public ContractResponseDTO toResponse(Contract contract) {
        Application application = contract.getApplication();
        User client = findClient(application.getProject().getClientId());
        // El plazo del contrato es el que propuso el freelancer (contraoferta) o, si no propuso, el del proyecto
        LocalDate deadline = application.getProposedDeadline() != null
                ? application.getProposedDeadline()
                : application.getProject().getDeadline();
        // Pago en dos partes: las cuotas y cuánto es del estudiante y cuánto de la comisión
        List<InstallmentResponseDTO> installments = installmentService.toResponse(contract);
        boolean hasPlan = !installments.isEmpty();
        boolean paid = hasPlan
                ? installments.stream().noneMatch(i -> InstallmentStatus.PENDING.name().equals(i.status()))
                : contract.getPayment().getStripeReference() != null;
        return new ContractResponseDTO(
                contract.getId(),
                application.getProject().getTitle(),
                application.getFreelancer().getUser().getNombre(),
                contract.getStatus().name(),
                contract.getPayment().getAmount(),
                contract.getPayment().getStatus().name(),
                contract.getStartedAt(),
                contract.getDeliveredAt(),
                application.getProject().getId(),
                application.getProject().getClientId(),
                application.getFreelancer().getUser().getId(),
                client != null ? client.getNombre() : null,
                deadline,
                paid,
                // DEVUTEC no comparte celulares: todo se coordina dentro de la plataforma (decisión del equipo).
                // Los campos quedan en null para no romper versiones anteriores de la web y la app.
                null,
                null,
                contract.getDeliveryUrl(),
                contract.getDeliveryNote(),
                contract.getStages().stream().map(this::toStageResponse).toList(),
                ContractStagePlan.currentStage(contract).map(ContractStage::getPosition).orElse(null),
                ContractStagePlan.emergencyFund(contract, contract.getPayment().getAmount()),
                ContractStagePlan.releasedAmount(contract),
                installments,
                hasPlan ? sum(installments, InstallmentResponseDTO::studentAmount) : null,
                hasPlan ? sum(installments, InstallmentResponseDTO::commissionAmount) : null,
                installments.stream()
                        .filter(i -> contract.getStatus() == ContractStatus.IN_PROGRESS || contract.getStatus() == ContractStatus.DELIVERED)
                        .filter(i -> i.number() == 2 && InstallmentStatus.PENDING.name().equals(i.status()) && i.dueAt() != null)
                        .map(InstallmentResponseDTO::dueAt)
                        .findFirst().orElse(null),
                contract.getClosedReason(),
                contract.getClosedAt()
        );
    }

    private static BigDecimal sum(List<InstallmentResponseDTO> installments,
                                  Function<InstallmentResponseDTO, BigDecimal> amount) {
        return installments.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ContractStageResponseDTO toStageResponse(ContractStage stage) {
        return new ContractStageResponseDTO(
                stage.getId(),
                stage.getPosition(),
                stage.getName(),
                stage.getPercentage(),
                stage.getAmount(),
                stage.getPaymentPart(),
                stage.getStatus().name(),
                stage.getDueDate(),
                stage.getDeliveryUrl(),
                stage.getDeliveryNote(),
                stage.getDeliveredAt(),
                stage.getChangeRounds(),
                ContractStage.MAX_CHANGE_ROUNDS,
                stage.getChangeRequestNote(),
                stage.getApprovedAt()
        );
    }

    // El proyecto guarda solo el id del cliente, así que el cliente (nombre y celular) se busca en usuarios
    private User findClient(Long clientId) {
        if (clientId == null) {
            return null;
        }
        return userRepository.findById(clientId).orElse(null);
    }
}
