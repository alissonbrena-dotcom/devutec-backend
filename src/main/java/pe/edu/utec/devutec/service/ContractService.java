package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.dto.ContractResponseDTO;
import pe.edu.utec.devutec.model.application.Application;

import java.util.List;

public interface ContractService {
    void createFromApplication(Application application);
    List<ContractResponseDTO> findMine();
    ContractResponseDTO findById(Long id);
    ContractResponseDTO deliver(Long id);
    ContractResponseDTO confirm(Long id);
    ContractResponseDTO cancel(Long id);
}