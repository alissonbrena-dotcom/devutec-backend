package pe.edu.utec.devutec.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
@Getter
public class UserRegisteredEvent extends ApplicationEvent {
    private final String email;
    private final String nombre;
    private final String rol;

    public UserRegisteredEvent(Object source, String email, String nombre, String rol) {
        super(source);
        this.email = email;
        this.nombre = nombre;
        this.rol = rol;
    }
}