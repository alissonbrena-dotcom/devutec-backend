package pe.edu.utec.devutec.exceptions;

import pe.edu.utec.devutec.model.PaymentStatus;

public class InvalidPaymentStateException extends ConflictException {
    public InvalidPaymentStateException(String action, PaymentStatus currentStatus) {
        super("Solo se puede " + action + " un pago retenido (HELD); estado actual: " + currentStatus);
    }
}
