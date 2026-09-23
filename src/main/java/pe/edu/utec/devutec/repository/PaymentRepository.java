package pe.edu.utec.devutec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.devutec.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
