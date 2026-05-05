package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.Store;
import com.licenta.licentabackend.dto.CreateStoreRequest;
import com.licenta.licentabackend.dto.EmployeeSummaryDto;
import com.licenta.licentabackend.dto.ManagerSummaryDto;
import com.licenta.licentabackend.dto.StoreDto;
import com.licenta.licentabackend.dto.StoreStaffResponse;
import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.StoreRepository;
import com.licenta.licentabackend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public AdminController(
            StoreRepository storeRepository,
            UserRepository userRepository,
            EmployeeRepository employeeRepository
    ) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
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

        StoreDto responseDto = new StoreDto(savedStore.getId(), savedStore.getStoreName());

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

}
