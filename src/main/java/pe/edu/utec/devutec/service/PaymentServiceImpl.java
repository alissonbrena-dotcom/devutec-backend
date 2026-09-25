package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.dto.PaymentResponseDTO;
import pe.edu.utec.devutec.events.PaymentReleasedEvent;
import pe.edu.utec.devutec.exceptions.InvalidPaymentStateException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.repository.PaymentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository repository;
    private final ApplicationEventPublisher publisher;
    private final ModelMapper modelMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> list() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponseDTO findById(Long id) {
        return toResponse(findPayment(id));
    }

    @Override
    @Transactional
    public PaymentResponseDTO releasePayment(Long id) {
        Payment payment = findPayment(id);
        ensureIsHeld(payment, "liberar");

        payment.setStatus(PaymentStatus.RELEASED);
        Payment saved = repository.save(payment);

        publisher.publishEvent(new PaymentReleasedEvent(this, saved.getId(), saved.getAmount()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentResponseDTO refundPayment(Long id) {
        Payment payment = findPayment(id);
        ensureIsHeld(payment, "reembolsar");

        payment.setStatus(PaymentStatus.REFUNDED);
        return toResponse(repository.save(payment));
    }

    private Payment findPayment(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado con id: " + id));
    }

    private void ensureIsHeld(Payment payment, String action) {
        if (payment.getStatus() != PaymentStatus.HELD) {
            throw new InvalidPaymentStateException(action, payment.getStatus());
        }
    }

    private PaymentResponseDTO toResponse(Payment payment) {
        return modelMapper.map(payment, PaymentResponseDTO.class);
    }
}
