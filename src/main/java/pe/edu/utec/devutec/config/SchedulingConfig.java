package pe.edu.utec.devutec.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Tareas programadas: por ahora, el job del pago 2 (recordatorios y cierre por falta de pago)
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
