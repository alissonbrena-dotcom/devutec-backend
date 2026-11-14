package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Cuerpo opcional al empezar el pago. La app manda el link para volver a ella cuando termina el pago
// (devutec://... en el APK, exp://... en Expo Go). La web no manda nada.
public record CheckoutStartRequestDTO(
        @Size(max = 300, message = "El link para volver a la app es demasiado largo")
        @Pattern(regexp = "^(devutec|exps?)://\\S+$", message = "El link para volver a la app no es válido")
        String appReturnUrl
) {
}
