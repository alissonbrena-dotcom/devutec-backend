package pe.edu.utec.devutec.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.devutec.model.contract.Contract;

import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByApplication_Freelancer_User_Id(Long userId);

    List<Contract> findByApplication_Project_ClientId(Long clientId);

    @EntityGraph(attributePaths = {"application.project", "application.freelancer.user"})
    Optional<Contract> findByPayment_Id(Long paymentId);
}
