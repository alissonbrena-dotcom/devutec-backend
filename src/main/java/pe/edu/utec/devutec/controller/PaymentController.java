package pe.edu.utec.devutec.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.service.PaymentService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @GetMapping
    public ResponseEntity<List<Payment>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<Payment> create(@RequestParam BigDecimal amount) {
        Payment payment = service.createPayment(amount);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @PatchMapping("/{id}/release")
    public ResponseEntity<Payment> release(@PathVariable Long id) {
        return ResponseEntity.ok(service.releasePayment(id));
    }

    @PatchMapping("/{id}/refund")
    public ResponseEntity<Payment> refund(@PathVariable Long id) {
        return ResponseEntity.ok(service.refundPayment(id));
    }
}
