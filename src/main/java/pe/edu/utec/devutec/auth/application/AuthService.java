package pe.edu.utec.devutec.auth.application;

import pe.edu.utec.devutec.auth.dto.AuthResponse;
import pe.edu.utec.devutec.auth.dto.LoginRequest;
import pe.edu.utec.devutec.auth.dto.RefreshTokenRequest;
import pe.edu.utec.devutec.auth.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refresh(RefreshTokenRequest request);
}
