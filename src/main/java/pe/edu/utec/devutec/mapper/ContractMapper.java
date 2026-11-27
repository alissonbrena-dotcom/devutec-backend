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

@Component
@RequiredArgsConstructor
public class ContractMapper {

    private final UserRepository userRepository;

    public ContractResponseDTO toResponse(Contract contract) {
        Application application = contract.getApplication();
        User client = findClient(application.getProject().getClientId());
        // Con el contrato cancelado ya no hay trabajo en común, así que no se comparten los celulares
        boolean sharePhones = contract.getStatus() != ContractStatus.CANCELLED;
        // El plazo del contrato es el que propuso el freelancer (contraoferta) o, si no propuso, el del proyecto
        LocalDate deadline = application.getProposedDeadline() != null
                ? application.getProposedDeadline()
                : application.getProject().getDeadline();
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
                contract.getPayment().getStripeReference() != null,
                sharePhones && client != null ? client.getCelular() : null,
                sharePhones ? application.getFreelancer().getUser().getCelular() : null,
                contract.getDeliveryUrl(),
                contract.getDeliveryNote(),
                contract.getStages().stream().map(this::toStageResponse).toList(),
                ContractStagePlan.currentStage(contract).map(ContractStage::getPosition).orElse(null),
                ContractStagePlan.emergencyFund(contract, contract.getPayment().getAmount()),
                ContractStagePlan.releasedAmount(contract)
        );
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
