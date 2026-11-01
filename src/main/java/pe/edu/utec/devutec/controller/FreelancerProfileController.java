package pe.edu.utec.devutec.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.devutec.dto.FreelancerProfileResponseDTO;
import pe.edu.utec.devutec.dto.FreelancerProfileUpdateDTO;
import pe.edu.utec.devutec.service.FreelancerProfileService;

@RestController
@RequestMapping("/api/v1/freelancers")
@RequiredArgsConstructor
public class FreelancerProfileController {

    private final FreelancerProfileService freelancerProfileService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    public ResponseEntity<FreelancerProfileResponseDTO> getMyProfile() {
        return ResponseEntity.ok(freelancerProfileService.getMyProfile());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('FREELANCER')")
    public ResponseEntity<FreelancerProfileResponseDTO> updateMyProfile(@Valid @RequestBody FreelancerProfileUpdateDTO dto) {
        return ResponseEntity.ok(freelancerProfileService.updateMyProfile(dto));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<FreelancerProfileResponseDTO> findByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(freelancerProfileService.findByUserId(userId));
    }
}
