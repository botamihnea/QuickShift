package com.licenta.licentabackend.security.auth;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Notification;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.domain.Store;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.NotificationRepository;
import com.licenta.licentabackend.repository.StoreRepository;
import com.licenta.licentabackend.repository.UserRepository;
import com.licenta.licentabackend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
        private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            StoreRepository storeRepository,
                        EmployeeRepository employeeRepository,
                        NotificationRepository notificationRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.storeRepository = storeRepository;
        this.employeeRepository = employeeRepository;
                this.notificationRepository = notificationRepository;
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

}
