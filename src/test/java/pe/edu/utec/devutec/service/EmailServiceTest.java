package pe.edu.utec.devutec.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;
import pe.edu.utec.devutec.exceptions.EmailSendingException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    // TEST 1 - Con el servidor de correo disponible, el correo se envía
    @Test
    void sendHtmlEmail_servidorDisponible_deberiaEnviarCorreo() {
        // Given
        MimeMessage message = new MimeMessage((Session) null);
        when(templateEngine.process(eq("payment-released"), any(IContext.class))).thenReturn("<p>ok</p>");
        when(mailSender.createMimeMessage()).thenReturn(message);

        // When
        emailService.sendHtmlEmail("camila@test.com", "Asunto", "payment-released", Map.of());

        // Then
        verify(mailSender).send(message);
    }

    // TEST 2 - Si el servidor de correo falla, se lanza EmailSendingException con el destinatario y la plantilla
    @Test
    void sendHtmlEmail_servidorCaido_deberiaLanzarEmailSendingException() {
        // Given
        MimeMessage message = new MimeMessage((Session) null);
        when(templateEngine.process(eq("payment-released"), any(IContext.class))).thenReturn("<p>ok</p>");
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("SMTP no disponible")).when(mailSender).send(message);

        // When + Then
        EmailSendingException ex = assertThrows(EmailSendingException.class,
                () -> emailService.sendHtmlEmail("camila@test.com", "Asunto", "payment-released", Map.of()));
        assertEquals("camila@test.com", ex.getRecipient());
        assertEquals("payment-released", ex.getTemplateName());
        assertInstanceOf(MailSendException.class, ex.getCause());
    }
}
