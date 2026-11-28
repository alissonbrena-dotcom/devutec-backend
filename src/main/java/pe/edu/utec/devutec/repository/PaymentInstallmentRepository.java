package pe.edu.utec.devutec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.devutec.model.InstallmentStatus;
import pe.edu.utec.devutec.model.PaymentInstallment;
import pe.edu.utec.devutec.model.contract.ContractStatus;

import java.util.Collection;
import java.util.List;

public interface PaymentInstallmentRepository extends JpaRepository<PaymentInstallment, Long> {

    List<PaymentInstallment> findByContract_IdOrderByNumberAsc(Long contractId);

    boolean existsByContract_Id(Long contractId);

    // Pagos con plazo corriendo, todavía sin pagar y con el contrato abierto: los revisa el job de recordatorios y cierre
    List<PaymentInstallment> findByStatusAndDueAtIsNotNullAndContract_StatusIn(InstallmentStatus status,
                                                                             Collection<ContractStatus> contractStatuses);
}
