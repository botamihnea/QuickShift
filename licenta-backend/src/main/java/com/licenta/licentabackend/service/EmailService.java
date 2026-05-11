package com.licenta.licentabackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "Reset your QuickShift password";
        String body = "We received a password reset request.\n\n"
                + "Use this link within 15 minutes to set a new password:\n"
                + resetLink + "\n\n"
                + "If you did not request this, you can ignore this email.";
        sendEmail(to, subject, body);
    }

    public void sendManagerWelcomeEmail(String to, String tempPassword) {
        String subject = "Your QuickShift manager account";
        String body = "Welcome to QuickShift. Here is your provisional account:\n\n"
            + "Login email: " + to + "\n"
            + "Temporary password: " + tempPassword + "\n\n"
            + "Please change your password once you are logged in.";
        sendEmail(to, subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
