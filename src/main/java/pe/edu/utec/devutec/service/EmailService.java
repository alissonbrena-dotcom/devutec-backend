package pe.edu.utec.devutec.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pe.edu.utec.devutec.exceptions.EmailSendingException;

import java.io.UnsupportedEncodingException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    // Quién aparece como remitente. Con Gmail debe ser la misma cuenta que envía.
    @Value("${app.mail.from:no-reply@devutec.com}")
    private String from = "no-reply@devutec.com";

    // Dirección de la web, para el botón "Ir a DevUTEC" de los correos
    @Value("${app.web-url:https://devutec.vercel.app}")
    private String webUrl = "https://devutec.vercel.app";

    @Async
    public void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            context.setVariable("webUrl", webUrl);
            String htmlContent = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(from, "DevUTEC");

            mailSender.send(message);
            log.info("Correo '{}' enviado a {}", templateName, to);
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            throw new EmailSendingException(to, templateName, e);
        }
    }
}