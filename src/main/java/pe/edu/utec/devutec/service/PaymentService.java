package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.dto.PaymentResponseDTO;

import java.util.List;

public interface PaymentService {
    List<PaymentResponseDTO> list();
    PaymentResponseDTO findById(Long id);
    PaymentResponseDTO releasePayment(Long id);
    PaymentResponseDTO refundPayment(Long id);
}
