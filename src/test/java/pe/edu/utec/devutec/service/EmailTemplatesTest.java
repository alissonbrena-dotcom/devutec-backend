package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Renderiza las plantillas de correo con Thymeleaf real, como lo hace EmailService.
// Deja una copia en target/email-preview/ para abrirlas en el navegador.
class EmailTemplatesTest {

    private static SpringTemplateEngine engine;

    @BeforeAll
    static void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
    }

    private static Map<String, Object> base(String actionUrl) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("appUrl", "https://devutec.vercel.app");
        variables.put("actionUrl", actionUrl);
        return variables;
    }

    private static String render(String template, Map<String, Object> variables) throws IOException {
        Context context = new Context();
        context.setVariables(variables);
        String html = engine.process(template, context);
        Path preview = Path.of("target", "email-preview", template + ".html");
        Files.createDirectories(preview.getParent());
        Files.writeString(preview, html);
        return html;
    }

    private static void assertCommon(String html, String actionUrl) {
        assertFalse(html.contains("${"), "quedó una variable sin reemplazar");
        assertFalse(html.matches("(?s).*\\sth:[a-z]+=.*"), "quedó un atributo de Thymeleaf");
        assertTrue(html.contains("href=\"" + actionUrl + "\""), "falta el link del botón");
        assertTrue(html.contains("https://devutec.vercel.app/email/logo-devutec.png"));
        assertTrue(html.contains("Este es un correo automático de DevUTEC"));
    }

    // TEST 1: nueva postulación, al cliente
    @Test
    void applicationReceived_deberiaMostrarElPostulanteYElLink() throws IOException {
        Map<String, Object> variables = base("https://devutec.vercel.app/projects/56/applications");
        variables.put("clientName", "Pizzería Don Mario");
        variables.put("freelancerName", "Camila Ríos");
        variables.put("projectTitle", "Web para pizzería");

        String html = render("application-received", variables);

        assertCommon(html, "https://devutec.vercel.app/projects/56/applications");
        assertTrue(html.contains("<title>Camila Ríos postuló a «Web para pizzería»</title>"));
        assertTrue(html.contains("Ver postulantes"));
        assertTrue(html.contains("cuyi-saluda.png"));
    }

    // TEST 2: postulación aceptada, al freelancer
    @Test
    void applicationAccepted_deberiaMostrarLaGarantia() throws IOException {
        Map<String, Object> variables = base("https://devutec.vercel.app/contracts");
        variables.put("freelancerName", "Camila Ríos");
        variables.put("projectTitle", "Web para pizzería");

        String html = render("application-accepted", variables);

        assertCommon(html, "https://devutec.vercel.app/contracts");
        assertTrue(html.contains("Tu pago está protegido en garantía."));
        assertTrue(html.contains("cuyi-celebra.png"));
    }

    // TEST 3: trabajo entregado, al cliente: el paso actual es "Entregado"
    @Test
    void contractDelivered_deberiaMarcarElPasoEntregado() throws IOException {
        Map<String, Object> variables = base("https://devutec.vercel.app/contracts/21");
        variables.put("clientName", "Pizzería Don Mario");
        variables.put("freelancerName", "Camila Ríos");
        variables.put("projectTitle", "Web para pizzería");

        String html = render("contract-delivered", variables);

        assertCommon(html, "https://devutec.vercel.app/contracts/21");
        assertTrue(html.contains("Revisar la entrega"));
        assertEquals(1, html.split("&#10003;|✓", -1).length - 1, "solo Retenido debería estar completo");
    }

    // TEST 4: pago liberado: monto grande partido en "S/ 1,600" y ".50", y el código del pago
    @Test
    void paymentReleased_deberiaMostrarElMontoYElCodigo() throws IOException {
        Map<String, Object> variables = base("https://devutec.vercel.app/contracts/21");
        variables.put("freelancerName", "Camila Ríos");
        variables.put("projectTitle", "Web para pizzería");
        variables.put("amount", new BigDecimal("1600.5"));
        variables.put("paymentId", 7L);

        String html = render("payment-released", variables);

        assertCommon(html, "https://devutec.vercel.app/contracts/21");
        assertTrue(html.contains("<title>Tu pago de S/ 1,600.50 fue liberado</title>"));
        assertTrue(html.contains("1,600<span style=\"color:#9e9b94\">.50</span>"));
        assertTrue(html.contains("#PG-0007"));
        assertEquals(3, html.split("&#10003;|✓", -1).length - 1, "los 3 pasos deberían estar completos");
    }

    // TEST 5: reseña con comentario y 4 estrellas
    @Test
    void reviewReceived_conComentario_deberiaMostrarloConLasEstrellas() throws IOException {
        Map<String, Object> variables = base("https://devutec.vercel.app/freelancers/12");
        variables.put("revieweeName", "Camila Ríos");
        variables.put("authorName", "Pizzería Don Mario");
        variables.put("projectTitle", "Web para pizzería");
        variables.put("rating", 4);
        variables.put("comment", "Entregó antes de la fecha.");
        variables.put("actionLabel", "Ver mi perfil");

        String html = render("review-received", variables);

        assertCommon(html, "https://devutec.vercel.app/freelancers/12");
        assertTrue(html.contains("«Entregó antes de la fecha.»"));
        assertTrue(html.contains("Ver mi perfil"));
        assertEquals(4, html.split("color:#b7791f\">(&#9733;|★)", -1).length - 1, "4 estrellas llenas");
        assertEquals(1, html.split("color:#d9d5cc\">(&#9733;|★)", -1).length - 1, "1 estrella vacía");
    }

    // TEST 6: reseña sin comentario: no aparece el bloque de la cita
    @Test
    void reviewReceived_sinComentario_noDeberiaMostrarLaCita() throws IOException {
        Map<String, Object> variables = base("https://devutec.vercel.app/contracts/21");
        variables.put("revieweeName", "Pizzería Don Mario");
        variables.put("authorName", "Camila Ríos");
        variables.put("projectTitle", "Web para pizzería");
        variables.put("rating", 5);
        variables.put("comment", null);
        variables.put("actionLabel", "Ver la reseña");

        String html = render("review-received", variables);

        assertCommon(html, "https://devutec.vercel.app/contracts/21");
        assertFalse(html.contains("font-size:22px;line-height:30px"), "no debería estar el bloque de la cita");
        assertTrue(html.contains("Ver la reseña"));
    }

    // TEST 7: bienvenida a un freelancer: pasos para postular y botón a su perfil
    @Test
    void welcome_freelancer_deberiaMostrarSusPasos() throws IOException {
        Map<String, Object> variables = base("https://devutec.vercel.app/profile/edit");
        variables.put("name", "Camila Ríos");
        variables.put("isFreelancer", true);

        String html = render("welcome", variables);

        assertCommon(html, "https://devutec.vercel.app/profile/edit");
        assertTrue(html.contains("<title>Bienvenido a DevUTEC, Camila Ríos</title>"));
        assertTrue(html.contains("Completa tu perfil"));
        assertTrue(html.contains("Completar mi perfil"));
        assertFalse(html.contains("Publica tu proyecto"));
    }

    // TEST 8: bienvenida a un cliente: pasos para publicar
    @Test
    void welcome_cliente_deberiaMostrarSusPasos() throws IOException {
        Map<String, Object> variables = base("https://devutec.vercel.app/projects/new");
        variables.put("name", "Pizzería Don Mario");
        variables.put("isFreelancer", false);

        String html = render("welcome", variables);

        assertCommon(html, "https://devutec.vercel.app/projects/new");
        assertTrue(html.contains("Publica tu proyecto"));
        assertTrue(html.contains("Libera el pago al aprobar la entrega"));
        assertTrue(html.contains("Publicar un proyecto"));
        assertFalse(html.contains("Completa tu perfil"));
    }

    // TEST 9: postulación rechazada, al freelancer
    @Test
    void applicationRejected_deberiaMostrarElProyectoYElLink() throws IOException {
        Map<String, Object> variables = base("https://devutec.vercel.app/projects");
        variables.put("freelancerName", "Luis Pérez");
        variables.put("projectTitle", "Web para pizzería");

        String html = render("application-rejected", variables);

        assertCommon(html, "https://devutec.vercel.app/projects");
        assertTrue(html.contains("<title>Novedades sobre tu postulación a «Web para pizzería»</title>"));
        assertTrue(html.contains("decidió seguir con otra opción"));
        assertTrue(html.contains("Buscar proyectos"));
    }

    // TEST 10: contrato creado, al cliente: monto en garantía
    @Test
    void contractCreated_deberiaMostrarElMontoEnGarantia() throws IOException {
        Map<String, Object> variables = base("https://devutec.vercel.app/contracts/7");
        variables.put("clientName", "Pizzería Don Mario");
        variables.put("freelancerName", "Camila Ríos");
        variables.put("projectTitle", "Web para pizzería");
        variables.put("amount", new BigDecimal("1250.5"));

        String html = render("contract-created", variables);

        assertCommon(html, "https://devutec.vercel.app/contracts/7");
        assertTrue(html.contains("<title>Tu contrato con Camila Ríos está en marcha</title>"));
        assertTrue(html.contains("1,250"));
        assertTrue(html.contains("Ver el contrato"));
    }
}
