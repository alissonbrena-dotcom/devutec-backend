package pe.edu.utec.devutec.guide;

import jakarta.validation.constraints.NotNull;

public record TaskUpdateDTO(@NotNull(message = "Indica si la tarea está hecha") Boolean done) {
}
