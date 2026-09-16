package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;

    public List<Payment> list() {
        return repository.findAll();
    }

    public Payment findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));
    }

    public Payment createPayment(BigDecimal amount) {
        Payment payment = new Payment();
        payment.setAmount(amount);
        // el status se pone en HELD solo, gracias al @PrePersist
        return repository.save(payment);
    }

    public Payment releasePayment(Long id) {
        Payment payment = findById(id);

        if (payment.getStatus() != PaymentStatus.HELD) {
            throw new RuntimeException("Solo se puede liberar un pago que está retenido (HELD)");
        }

        payment.setStatus(PaymentStatus.RELEASED);
        return repository.save(payment);
    }

    public Payment refundPayment(Long id) {
        Payment payment = findById(id);

        if (payment.getStatus() != PaymentStatus.HELD) {
            throw new RuntimeException("Solo se puede reembolsar un pago que está retenido (HELD)");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        return repository.save(payment);
    }
}