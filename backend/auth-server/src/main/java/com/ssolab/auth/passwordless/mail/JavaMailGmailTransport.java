package com.ssolab.auth.passwordless.mail;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
@Profile("!local & !test")
public class JavaMailGmailTransport implements GmailMailTransport {

    @Override
    public void send(
        GmailSmtpProperties properties,
        String recipient,
        String subject,
        String body,
        char[] password
    ) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setUsername(properties.username());
        sender.setPassword(new String(password));
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties javaMail = sender.getJavaMailProperties();
        javaMail.put("mail.smtp.auth", "true");
        javaMail.put("mail.smtp.starttls.enable", "true");
        javaMail.put("mail.smtp.starttls.required", "true");
        javaMail.put("mail.smtp.connectiontimeout",
            Long.toString(properties.connectionTimeout().toMillis()));
        javaMail.put("mail.smtp.timeout", Long.toString(properties.readTimeout().toMillis()));
        javaMail.put("mail.smtp.writetimeout",
            Long.toString(properties.writeTimeout().toMillis()));

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(recipient);
        mail.setSubject(subject);
        mail.setText(body);
        try {
            sender.send(mail);
        } catch (MailException exception) {
            throw new MailDeliveryUnavailableException(exception);
        } finally {
            sender.setPassword(null);
        }
    }
}
