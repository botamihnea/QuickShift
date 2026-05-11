package com.licenta.licentabackend.security.auth;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Notification;
import com.licenta.licentabackend.domain.PasswordResetToken;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.domain.Store;
import com.licenta.licentabackend.dto.ChangePasswordRequest;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.NotificationRepository;
import com.licenta.licentabackend.repository.PasswordResetTokenRepository;
import com.licenta.licentabackend.repository.StoreRepository;
import com.licenta.licentabackend.repository.UserRepository;
import com.licenta.licentabackend.security.JwtService;
import com.licenta.licentabackend.service.EmailService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
        private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
        private final PasswordResetTokenRepository passwordResetTokenRepository;
        private final EmailService emailService;
        private final String frontendUrl;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            StoreRepository storeRepository,
                                                EmployeeRepository employeeRepository,
                                                NotificationRepository notificationRepository,
                                                PasswordResetTokenRepository passwordResetTokenRepository,
                                                EmailService emailService,
                                                @Value("${app.frontend.url}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.storeRepository = storeRepository;
        this.employeeRepository = employeeRepository;
                this.notificationRepository = notificationRepository;
                this.passwordResetTokenRepository = passwordResetTokenRepository;
                this.emailService = emailService;
                this.frontendUrl = frontendUrl;
    }

    public AuthenticationResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new IllegalArgumentException("Store not found with ID: " + request.storeId()));

        AppUser user = new AppUser(
                request.email(),
                passwordEncoder.encode(request.password()),
                Role.EMPLOYEE, //default for now,
                store
        );

        userRepository.save(user);

        Employee employee = new Employee();
        employee.setFullName(request.fullName());
        employee.setContractType(request.contractType());
        employee.setShiftPreference(request.shiftPreference());
        employee.setRemainingLeaveDays(21);
        employee.setHolidayRecoveryHours(0);
        employee.setStore(store);
        employee.setAppUser(user);
        employeeRepository.save(employee);

                List<AppUser> managers = userRepository.findByStoreIdAndRole(store.getId(), Role.MANAGER);
                if (!managers.isEmpty()) {
                        String message = "New employee registered: " + employee.getFullName();
                        for (AppUser manager : managers) {
                                notificationRepository.save(new Notification(message, manager, store));
                        }
                }

        String jwtToken = jwtService.generateToken(user);
        return new AuthenticationResponse(jwtToken);
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        AppUser user = userRepository.findByEmail(request.email()).orElseThrow();

        String jwtToken = jwtService.generateToken(user);
        return new AuthenticationResponse(jwtToken);
    }

    public AuthenticatedUserResponse getCurrentUser(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found."));

        Long storeId = user.getStore() != null ? user.getStore().getId() : null;
        String storeName = user.getStore() != null ? user.getStore().getStoreName() : null;

        return new AuthenticatedUserResponse(
                user.getEmail(),
                user.getRole().name(),
                storeId,
                storeName
        );
    }

        @Transactional
        public void forgotPassword(String email) {
                userRepository.findByEmail(email).ifPresent(user -> {
                        passwordResetTokenRepository.deleteByAppUser(user);
                        String token = UUID.randomUUID().toString();
                        PasswordResetToken resetToken = new PasswordResetToken(
                                        token,
                                        user,
                                        LocalDateTime.now().plusMinutes(15),
                                        false
                        );
                        passwordResetTokenRepository.save(resetToken);

                        String resetLink = frontendUrl + "/reset-password?token=" + token;
                        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
                });
        }

        public void resetPassword(String token, String newPassword) {
                PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token."));

                if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                        throw new IllegalArgumentException("Invalid or expired token.");
                }

                AppUser user = resetToken.getAppUser();
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                resetToken.setUsed(true);
                passwordResetTokenRepository.save(resetToken);
        }

        public void changePassword(String email, ChangePasswordRequest request) {
                AppUser user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found."));

                if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                        throw new IllegalArgumentException("Current password is incorrect.");
                }

                user.setPassword(passwordEncoder.encode(request.newPassword()));
                userRepository.save(user);
        }

}
