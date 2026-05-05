package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.dto.EmployeeSummaryDto;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import com.licenta.licentabackend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final ShiftRepository shiftRepository;

    public EmployeeController(
            EmployeeRepository employeeRepository,
            UserRepository userRepository,
            ShiftRepository shiftRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.shiftRepository = shiftRepository;
    }

    @GetMapping
    public ResponseEntity<List<EmployeeSummaryDto>> getEmployees(
            Authentication authentication,
            @RequestParam(required = false) Long storeId
    ) {
        AppUser user = resolveCurrentUser(authentication);

        List<Employee> employees;
        if (user.getRole() == Role.ADMIN) {
            if (storeId == null) {
                return ResponseEntity.badRequest().build();
            }
            employees = employeeRepository.findByStoreId(storeId);
        } else {
            Long scopedStoreId = resolveStoreId(user);
            employees = employeeRepository.findByStoreId(scopedStoreId);
        }

        List<EmployeeSummaryDto> result = employees.stream()
                .map(this::toDto)
                .toList();

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id, Authentication authentication) {
        AppUser user = resolveCurrentUser(authentication);
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found."));

        if (user.getRole() != Role.ADMIN) {
            Long storeId = resolveStoreId(user);
            if (employee.getStore() == null || !storeId.equals(employee.getStore().getId())) {
                return ResponseEntity.status(403).build();
            }
        }

        Long userId = employee.getAppUser() != null ? employee.getAppUser().getId() : null;

        shiftRepository.deleteByEmployeeId(employee.getId());
        employee.setAppUser(null);
        employeeRepository.delete(employee);

        if (userId != null) {
            userRepository.deleteById(userId);
        }
        return ResponseEntity.noContent().build();
    }

    private AppUser resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("No authenticated user found.");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }

    private Long resolveStoreId(AppUser user) {
        if (user.getStore() == null || user.getStore().getId() == null) {
            throw new IllegalArgumentException("Your account is not assigned to a store.");
        }

        return user.getStore().getId();
    }

    private EmployeeSummaryDto toDto(Employee employee) {
        return new EmployeeSummaryDto(
                employee.getId(),
                employee.getFullName(),
                employee.getContractType(),
                employee.getShiftPreference(),
                employee.getRemainingLeaveDays(),
                employee.getHolidayRecoveryHours()
        );
    }
}