package pe.edu.utec.devutec.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;

@Component
@RequiredArgsConstructor
public class ContractMapper {

    private final UserRepository userRepository;

    public ContractResponseDTO toResponse(Contract contract) {
        Application application = contract.getApplication();
        User client = findClient(application.getProject().getClientId());
        // Con el contrato cancelado ya no hay trabajo en común, así que no se comparten los celulares
        boolean sharePhones = contract.getStatus() != ContractStatus.CANCELLED;
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
                application.getProject().getDeadline(),
                contract.getPayment().getStripeReference() != null,
                sharePhones && client != null ? client.getCelular() : null,
                sharePhones ? application.getFreelancer().getUser().getCelular() : null
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
