package pe.edu.utec.devutec.pricing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Carga las horas de referencia al arrancar. Solo agrega los códigos que faltan: si alguien ajustó las horas
// de una funcionalidad en la base de datos, no se pisan.
// IMPORTANTE: son valores iniciales para validar con el equipo (alguien que haya hecho proyectos reales).
@Slf4j
@Component
@RequiredArgsConstructor
public class FuncionalidadReferenciaSeeder implements ApplicationRunner {

    static final List<FuncionalidadReferencia> DEFAULTS = List.of(
            // Generales: sirven para cualquier proyecto
            new FuncionalidadReferencia("DESCUBRIMIENTO", "Reunión inicial y definición de requisitos", "GENERAL", 2, 4),
            new FuncionalidadReferencia("DISENO_VISUAL", "Diseño visual (colores, tipografía, maqueta)", "GENERAL", 6, 12),
            new FuncionalidadReferencia("DESPLIEGUE", "Publicación (dominio, hosting) y pruebas finales", "GENERAL", 2, 5),
            // Webs
            new FuncionalidadReferencia("PAGINA_INFO", "Página informativa", "WEB", 4, 6),
            new FuncionalidadReferencia("FORMULARIO", "Formulario de contacto o reservas", "WEB", 2, 4),
            new FuncionalidadReferencia("CARTA_CATALOGO", "Carta o catálogo de productos", "WEB", 6, 10),
            new FuncionalidadReferencia("CARRITO_PEDIDOS", "Carrito o pedidos en línea", "WEB", 15, 25),
            new FuncionalidadReferencia("PAGOS_EN_LINEA", "Pagos en línea", "WEB", 8, 14),
            new FuncionalidadReferencia("LOGIN", "Inicio de sesión de usuarios", "WEB", 8, 12),
            new FuncionalidadReferencia("PANEL_ADMIN", "Panel de administración", "WEB", 12, 20),
            new FuncionalidadReferencia("BLOG", "Blog o sección de noticias", "WEB", 6, 10),
            new FuncionalidadReferencia("RESERVAS", "Sistema de reservas o citas", "WEB", 12, 20),
            new FuncionalidadReferencia("WHATSAPP_REDES", "Botón de WhatsApp y enlaces a redes", "WEB", 1, 2),
            new FuncionalidadReferencia("SEO_BASICO", "Posicionamiento básico en Google (SEO)", "WEB", 2, 4),
            // Automatizaciones
            new FuncionalidadReferencia("AUTOMATIZACION_SIMPLE", "Automatización simple (hojas de cálculo, correos)", "AUTOMATIZACION", 4, 8),
            new FuncionalidadReferencia("BOT_MENSAJERIA", "Bot de mensajería (WhatsApp, Telegram)", "AUTOMATIZACION", 10, 18),
            new FuncionalidadReferencia("INTEGRACION_API", "Integración con otro sistema o API", "AUTOMATIZACION", 6, 12),
            new FuncionalidadReferencia("REPORTE_DASHBOARD", "Reporte o dashboard de datos", "AUTOMATIZACION", 8, 14),
            // Apps
            new FuncionalidadReferencia("PANTALLA_APP", "Pantalla de app móvil", "APP", 4, 8),
            new FuncionalidadReferencia("NOTIFICACIONES", "Notificaciones push", "APP", 4, 8),
            new FuncionalidadReferencia("PUBLICACION_TIENDA", "Publicación en tienda de apps", "APP", 4, 8)
    );

    private final FuncionalidadReferenciaRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<FuncionalidadReferencia> missing = DEFAULTS.stream()
                .filter(funcionalidad -> !repository.existsById(funcionalidad.getCodigo()))
                .toList();
        if (!missing.isEmpty()) {
            repository.saveAll(missing);
            log.info("Se cargaron {} funcionalidades de referencia", missing.size());
        }
    }
}
