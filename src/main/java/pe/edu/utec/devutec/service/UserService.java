package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.dto.UserResponseDTO;

public interface UserService {
    UserResponseDTO getCurrentUser();

    // Guarda o cambia el celular de la persona con sesión (9 dígitos que empiezan con 9)
    UserResponseDTO updatePhone(String celular);

    // Quita el celular: el botón de WhatsApp deja de aparecer en sus contratos
    UserResponseDTO deletePhone();
}
