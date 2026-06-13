package com.licenta.licentabackend.config;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    @Value("${admin.default.email}")
    private String adminEmail;

    @Value("${admin.default.password}")
    private String adminPassword;

    @Bean
    CommandLineRunner initDatabase(UserRepository repository, PasswordEncoder passwordEncoder) {
        return args -> {

            if (!repository.existsByRole(Role.ADMIN)) {

                AppUser admin = new AppUser(
                        adminEmail,
                        passwordEncoder.encode(adminPassword),
                        Role.ADMIN,
                        null
                );

                repository.save(admin);
                log.info("✅ Super Admin account created with email: {}", adminEmail);
            }
        };
    }
}
