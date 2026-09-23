package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import pe.edu.utec.devutec.dto.PaymentRequestDTO;
import pe.edu.utec.devutec.dto.PaymentResponseDTO;
import pe.edu.utec.devutec.events.PaymentReleasedEvent;
import pe.edu.utec.devutec.exceptions.ConflictException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.PaymentStatus;
import pe.edu.utec.devutec.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final ApplicationEventPublisher publisher;
    private final ModelMapper modelMapper;

    public List<PaymentResponseDTO> list() {
        return repository.findAll().stream().map(payment -> modelMapper.map(payment, PaymentResponseDTO.class)).toList();
    }

    public PaymentResponseDTO findById(Long id) {
        Payment payment = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));
        return modelMapper.map(payment, PaymentResponseDTO.class);
    }

    public PaymentResponseDTO createPayment(PaymentRequestDTO request) {
        Payment payment = modelMapper.map(request, Payment.class);
        Payment saved = repository.save(payment);
        return modelMapper.map(saved, PaymentResponseDTO.class);
    }

    public PaymentResponseDTO releasePayment(Long id) {
        Payment payment = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));

        if (payment.getStatus() != PaymentStatus.HELD) {
            throw new ConflictException("Solo se puede liberar un pago que está retenido (HELD)");
        }

        payment.setStatus(PaymentStatus.RELEASED);
        Payment saved = repository.save(payment);

        publisher.publishEvent(new PaymentReleasedEvent(this, saved));

        return modelMapper.map(saved, PaymentResponseDTO.class);
    }

    public PaymentResponseDTO refundPayment(Long id) {
        Payment payment = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));

        if (payment.getStatus() != PaymentStatus.HELD) {
            throw new ConflictException("Solo se puede reembolsar un pago que está retenido (HELD)");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        Payment saved = repository.save(payment);

        return modelMapper.map(saved, PaymentResponseDTO.class);
    }
}