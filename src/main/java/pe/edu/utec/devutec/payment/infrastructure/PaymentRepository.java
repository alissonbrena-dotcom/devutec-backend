package pe.edu.utec.devutec.payment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.devutec.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
