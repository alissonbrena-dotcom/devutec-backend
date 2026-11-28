package pe.edu.utec.devutec.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.devutec.payments.SecondPaymentJob;

// Solo admin: corre en el momento el job del pago 2 (recordatorios y cierre de vencidos), para mostrarlo en vivo
@RestController
@RequestMapping("/api/v1/admin/installments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInstallmentController {

    private final SecondPaymentJob job;

    @PostMapping("/run-reminders")
    public ResponseEntity<SecondPaymentJob.Summary> runReminders() {
        return ResponseEntity.ok(job.runNow());
    }
}
