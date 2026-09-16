package pe.edu.utec.devutec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())                                  // 👈 esto desbloquea POST/PATCH
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()); // 👈 esto quita el login
        return http.build();
    }
}