package pe.edu.utec.devutec.payments;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.devutec.dto.CheckoutConfirmRequestDTO;
import pe.edu.utec.devutec.dto.CheckoutResponseDTO;
import pe.edu.utec.devutec.dto.CheckoutStartRequestDTO;
import pe.edu.utec.devutec.dto.ContractResponseDTO;

@RestController
@RequestMapping("/api/v1/contracts/{id}/checkout")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT')")
public class CheckoutController {

    private final CheckoutService checkoutService;

    // Devuelve el link de la página de pago de Stripe. Desde la app llega appReturnUrl para volver a ella al terminar.
    @PostMapping
    public ResponseEntity<CheckoutResponseDTO> start(@PathVariable Long id,
                                                     @Valid @RequestBody(required = false) CheckoutStartRequestDTO request) {
        String appReturnUrl = request != null ? request.appReturnUrl() : null;
        Integer installment = request != null ? request.installment() : null;
        return ResponseEntity.ok(checkoutService.startCheckout(id, appReturnUrl, installment));
    }

    // Se llama al volver de Stripe, con el session_id que Stripe agrega al link de regreso
    @PostMapping("/confirm")
    public ResponseEntity<ContractResponseDTO> confirm(@PathVariable Long id,
                                                       @Valid @RequestBody CheckoutConfirmRequestDTO request) {
        return ResponseEntity.ok(checkoutService.confirmCheckout(id, request.sessionId()));
    }
}
