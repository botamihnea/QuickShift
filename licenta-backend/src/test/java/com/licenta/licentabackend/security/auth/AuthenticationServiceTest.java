package com.licenta.licentabackend.security.auth;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.domain.Store;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.NotificationRepository;
import com.licenta.licentabackend.repository.StoreRepository;
import com.licenta.licentabackend.repository.UserRepository;
import com.licenta.licentabackend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void registerCreatesUserAndEmployee() {
        Store store = new Store("Main Store", "Main St", 1200.0);
        store.setId(10L);

        RegisterRequest request = new RegisterRequest(
                "Ana Popescu",
                "ana@example.com",
                "secret123",
                "MORNING",
                "FULL_TIME_8H",
                10L
        );

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store));
        when(passwordEncoder.encode(request.password())).thenReturn("hashed");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByStoreIdAndRole(10L, Role.MANAGER)).thenReturn(List.of());
        when(jwtService.generateToken(any(AppUser.class))).thenReturn("jwt-token");

        AuthenticationResponse response = authenticationService.register(request);

        assertEquals("jwt-token", response.token());

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(userCaptor.capture());
        AppUser savedUser = userCaptor.getValue();

        assertEquals("ana@example.com", savedUser.getEmail());
        assertEquals(Role.EMPLOYEE, savedUser.getRole());
        assertEquals(store, savedUser.getStore());

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        Employee savedEmployee = employeeCaptor.getValue();

        assertEquals("Ana Popescu", savedEmployee.getFullName());
        assertEquals("FULL_TIME_8H", savedEmployee.getContractType());
        assertEquals("MORNING", savedEmployee.getShiftPreference());
        assertEquals(21, savedEmployee.getRemainingLeaveDays());
        assertEquals(0, savedEmployee.getHolidayRecoveryHours());
        assertEquals(store, savedEmployee.getStore());
        assertNotNull(savedEmployee.getAppUser());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "Ana Popescu",
                "ana@example.com",
                "secret123",
                "MORNING",
                "FULL_TIME_8H",
                10L
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(new AppUser("ana@example.com", "pass", Role.EMPLOYEE, null)));

        assertThrows(IllegalArgumentException.class, () -> authenticationService.register(request));
    }

    @Test
    void getCurrentUserReturnsStoreDetails() {
        Store store = new Store("Central", "Main St", 900.0);
        store.setId(5L);
        AppUser user = new AppUser("staff@example.com", "pass", Role.MANAGER, store);

        when(userRepository.findByEmail(eq("staff@example.com"))).thenReturn(Optional.of(user));

        AuthenticatedUserResponse response = authenticationService.getCurrentUser("staff@example.com");

        assertEquals("staff@example.com", response.email());
        assertEquals("MANAGER", response.role());
        assertEquals(5L, response.storeId());
        assertEquals("Central", response.storeName());
    }
}
