package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.Store;
import com.licenta.licentabackend.dto.CreateManagerRequest;
import com.licenta.licentabackend.dto.CreateStoreRequest;
import com.licenta.licentabackend.dto.EmployeeSummaryDto;
import com.licenta.licentabackend.dto.ManagerSummaryDto;
import com.licenta.licentabackend.dto.StoreDto;
import com.licenta.licentabackend.dto.StoreStaffResponse;
import com.licenta.licentabackend.dto.UpdateBusyDayThresholdRequest;
import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.StoreRepository;
import com.licenta.licentabackend.repository.UserRepository;
import com.licenta.licentabackend.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public AdminController(
            StoreRepository storeRepository,
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService
    ) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // Endpoint: POST /api/admin/stores
    @PostMapping("/stores")
    public ResponseEntity<StoreDto> createNewStore(@RequestBody CreateStoreRequest request) {

        if (storeRepository.findByStoreName(request.storeName()).isPresent()) {
            throw new IllegalArgumentException("A store with this name already exists.");
        }


        Store newStore = new Store(
                request.storeName(),
                request.address(),
                request.busyDaySalesThreshold()
        );

        Store savedStore = storeRepository.save(newStore);

        StoreDto responseDto = new StoreDto(
            savedStore.getId(),
            savedStore.getStoreName(),
            savedStore.getBusyDaySalesThreshold()
        );

        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/stores/{storeId}/staff")
    public ResponseEntity<StoreStaffResponse> getStoreStaff(@PathVariable Long storeId) {
        List<AppUser> managers = userRepository.findByStoreIdAndRole(storeId, Role.MANAGER);
        ManagerSummaryDto manager = managers.isEmpty()
                ? null
                : new ManagerSummaryDto(managers.get(0).getId(), managers.get(0).getEmail());

        List<EmployeeSummaryDto> employees = employeeRepository.findByStoreId(storeId).stream()
                .map(employee -> new EmployeeSummaryDto(
                        employee.getId(),
                        employee.getFullName(),
                        employee.getContractType(),
                        employee.getShiftPreference(),
                        employee.getRemainingLeaveDays(),
                        employee.getHolidayRecoveryHours()
                ))
                .toList();

        return ResponseEntity.ok(new StoreStaffResponse(manager, employees));
    }

    @GetMapping("/stores/unmanaged")
    public ResponseEntity<List<StoreDto>> getUnmanagedStores() {
        List<StoreDto> stores = storeRepository.findStoresWithNoManager().stream()
                .map(store -> new StoreDto(store.getId(), store.getStoreName(), store.getBusyDaySalesThreshold()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(stores);
    }

    @PostMapping("/managers")
    public ResponseEntity<String> createManager(@Valid @RequestBody CreateManagerRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.badRequest().body("Email already in use.");
        }

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new IllegalArgumentException("Store not found."));

        List<AppUser> managers = userRepository.findByStoreIdAndRole(store.getId(), Role.MANAGER);
        if (!managers.isEmpty()) {
            return ResponseEntity.badRequest().body("This store already has a manager.");
        }

        String tempPassword = generateTempPassword(12);
        AppUser manager = new AppUser(
                request.email().trim(),
                passwordEncoder.encode(tempPassword),
                Role.MANAGER,
                store
        );
        userRepository.save(manager);

        emailService.sendManagerWelcomeEmail(manager.getEmail(), tempPassword);

        return ResponseEntity.ok("Manager created successfully.");
    }

    @PutMapping("/stores/{storeId}/threshold")
    public ResponseEntity<StoreDto> updateStoreThreshold(
            @PathVariable Long storeId,
            @RequestBody UpdateBusyDayThresholdRequest request
    ) {
        if (request == null || request.busyDaySalesThreshold() == null || request.busyDaySalesThreshold() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found."));

        store.setBusyDaySalesThreshold(request.busyDaySalesThreshold());
        Store savedStore = storeRepository.save(store);

        return ResponseEntity.ok(new StoreDto(
                savedStore.getId(),
                savedStore.getStoreName(),
                savedStore.getBusyDaySalesThreshold()
        ));
    }

    private String generateTempPassword(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i += 1) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }

}
