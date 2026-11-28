package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Cuerpo opcional al empezar el pago. La app manda el link para volver a ella cuando termina el pago
// (devutec://... en el APK, exp://... en Expo Go). La web no manda nada.
public record CheckoutStartRequestDTO(
        @Size(max = 300, message = "El link para volver a la app es demasiado largo")
        @Pattern(regexp = "^(devutec|exps?)://\\S+$", message = "El link para volver a la app no es válido")
        String appReturnUrl,
        // Qué pago hacer en los contratos con pago en dos partes (1 o 2). Sin número, el siguiente pendiente.
        @Min(value = 1, message = "El número de pago no es válido")
        @Max(value = 2, message = "El número de pago no es válido")
        Integer installment
) {

}
