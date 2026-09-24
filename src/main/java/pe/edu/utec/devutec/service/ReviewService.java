package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.dto.ReviewCreateDTO;
import pe.edu.utec.devutec.dto.ReviewResponseDTO;

import java.util.List;

public interface ReviewService {
    ReviewResponseDTO create(Long contractId, ReviewCreateDTO dto);
    List<ReviewResponseDTO> findByContract(Long contractId);
}
