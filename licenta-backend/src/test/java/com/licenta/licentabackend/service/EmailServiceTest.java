package com.licenta.licentabackend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> messageCaptor;

    @Test
    void sendPasswordResetEmailBuildsExpectedMessage() {
        EmailService emailService = new EmailService(mailSender, "noreply@quickshift.com");

        emailService.sendPasswordResetEmail("user@example.com", "https://reset-link");

        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();

        assertEquals("noreply@quickshift.com", message.getFrom());
        assertEquals("user@example.com", message.getTo()[0]);
        assertEquals("Reset your QuickShift password", message.getSubject());
        assertTrue(message.getText().contains("https://reset-link"));
    }

    @Test
    void sendManagerWelcomeEmailBuildsExpectedMessage() {
        EmailService emailService = new EmailService(mailSender, "noreply@quickshift.com");

        emailService.sendManagerWelcomeEmail("boss@example.com", "temp-pass");

        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();

        assertEquals("noreply@quickshift.com", message.getFrom());
        assertEquals("boss@example.com", message.getTo()[0]);
        assertEquals("Your QuickShift manager account", message.getSubject());
        assertTrue(message.getText().contains("temp-pass"));
    }
}
