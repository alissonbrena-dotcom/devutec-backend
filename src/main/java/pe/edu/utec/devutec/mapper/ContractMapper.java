package pe.edu.utec.devutec.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;

@Component
public class ContractMapper {

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
                contract.getDeliveredAt()
        );
    }
}