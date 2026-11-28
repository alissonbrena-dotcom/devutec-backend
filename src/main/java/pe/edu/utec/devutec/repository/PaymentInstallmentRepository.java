package pe.edu.utec.devutec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.devutec.model.PaymentInstallment;

import java.util.List;

public interface PaymentInstallmentRepository extends JpaRepository<PaymentInstallment, Long> {

    List<PaymentInstallment> findByContract_IdOrderByNumberAsc(Long contractId);

    boolean existsByContract_Id(Long contractId);
}
