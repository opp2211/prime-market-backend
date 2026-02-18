package ru.maltsev.primemarketbackend.config;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

@Configuration
@Profile("dev")
public class DevMailConfig {
    @Bean
    public JavaMailSender javaMailSender() {
        return new LoggingMailSender();
    }
}

class LoggingMailSender implements JavaMailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);
    private final Session session = Session.getInstance(new Properties());

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(session);
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        try {
            return new MimeMessage(session, contentStream);
        } catch (MessagingException ex) {
            throw new MailPreparationException("Failed to parse MimeMessage", ex);
        }
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        logMimeMessage(mimeMessage);
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        for (MimeMessage message : mimeMessages) {
            logMimeMessage(message);
        }
    }

    @Override
    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        MimeMessage message = createMimeMessage();
        try {
            mimeMessagePreparator.prepare(message);
        } catch (Exception ex) {
            throw new MailPreparationException("Failed to prepare MimeMessage", ex);
        }
        logMimeMessage(message);
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        for (MimeMessagePreparator preparator : mimeMessagePreparators) {
            send(preparator);
        }
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        logSimpleMessage(simpleMessage);
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        for (SimpleMailMessage message : simpleMessages) {
            logSimpleMessage(message);
        }
    }

    private void logMimeMessage(MimeMessage message) {
        try {
            String subject = message.getSubject();
            String from = addressesToString(message.getFrom());
            String to = addressesToString(message.getAllRecipients());
            String contentType = message.getContentType();
            Object content = message.getContent();
            String body = (content instanceof String) ? (String) content : "<" + contentType + ">";

            log.info("[DEV MAIL] to={}, from={}, subject={}, body={}", to, from, subject, body);
        } catch (Exception ex) {
            throw new MailSendException("Failed to log MimeMessage", ex);
        }
    }

    private void logSimpleMessage(SimpleMailMessage message) {
        String to = message.getTo() == null ? "" : String.join(",", message.getTo());
        String from = message.getFrom();
        String subject = message.getSubject();
        String text = message.getText();

        log.info("[DEV MAIL] to={}, from={}, subject={}, body={}", to, from, subject, text);
    }

    private String addressesToString(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        return String.join(",", Arrays.stream(addresses)
            .map(Address::toString)
            .toList());
    }
}
