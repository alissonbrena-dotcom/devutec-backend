package pe.edu.utec.devutec.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;

@Component
@RequiredArgsConstructor
public class ContractMapper {

    private final UserRepository userRepository;

    public ContractResponseDTO toResponse(Contract contract) {
        Application application = contract.getApplication();
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
                clientName(application.getProject().getClientId()),
                application.getProject().getDeadline()
        );
    }

    // El proyecto guarda solo el id del cliente, así que el nombre se busca en usuarios
    private String clientName(Long clientId) {
        if (clientId == null) {
            return null;
        }
        return userRepository.findById(clientId).map(User::getNombre).orElse(null);
    }
}
