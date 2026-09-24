package pe.edu.utec.devutec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.devutec.model.contract.Contract;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByApplication_Freelancer_User_Id(Long userId);

    List<Contract> findByApplication_Project_ClientId(Long clientId);
}