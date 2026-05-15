package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.dto.EmployeeSelfDto;
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

import java.time.YearMonth;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        AppUser user = resolveCurrentUser(authentication);

        YearMonth targetMonth = resolveTargetMonth(year, month);
        LocalDate monthStart = targetMonth.atDay(1);
        LocalDate monthEnd = targetMonth.atEndOfMonth();

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

        Long effectiveStoreId = user.getRole() == Role.ADMIN ? storeId : resolveStoreId(user);
        Map<Long, Integer> plannedHoursByEmployee = calculatePlannedHours(effectiveStoreId, monthStart, monthEnd);

        List<EmployeeSummaryDto> result = employees.stream()
                .map(employee -> toDto(employee, plannedHoursByEmployee.get(employee.getId())))
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/me")
    public ResponseEntity<EmployeeSelfDto> getMyEmployee(Authentication authentication) {
        AppUser user = resolveCurrentUser(authentication);
        if (user.getRole() != Role.EMPLOYEE) {
            return ResponseEntity.status(403).build();
        }

        Employee employee = employeeRepository.findByAppUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found."));

        Long storeId = employee.getStore() != null ? employee.getStore().getId() : null;
        String storeName = employee.getStore() != null ? employee.getStore().getStoreName() : null;

        return ResponseEntity.ok(new EmployeeSelfDto(
                employee.getId(),
                employee.getFullName(),
                employee.getRemainingLeaveDays(),
                employee.getHolidayRecoveryHours(),
                storeId,
                storeName
        ));
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

    private EmployeeSummaryDto toDto(Employee employee, Integer plannedHours) {
        return new EmployeeSummaryDto(
                employee.getId(),
                employee.getFullName(),
                employee.getContractType(),
                employee.getShiftPreference(),
                employee.getRemainingLeaveDays(),
                employee.getHolidayRecoveryHours(),
                plannedHours
        );
    }

    private YearMonth resolveTargetMonth(Integer year, Integer month) {
        if (year == null && month == null) {
            return YearMonth.now().plusMonths(1);
        }

        if (year == null || month == null) {
            throw new IllegalArgumentException("Both year and month must be provided together.");
        }

        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12.");
        }

        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Year must be between 2000 and 2100.");
        }

        return YearMonth.of(year, month);
    }

    private Map<Long, Integer> calculatePlannedHours(Long storeId, LocalDate start, LocalDate end) {
        Map<Long, Integer> totals = new HashMap<>();
        if (storeId == null) {
            return totals;
        }

        List<com.licenta.licentabackend.domain.Shift> shifts =
                shiftRepository.findByEmployeeStoreIdAndShiftDateBetween(storeId, start, end);
        for (com.licenta.licentabackend.domain.Shift shift : shifts) {
            if ("ABSENT".equals(shift.getStatus())) {
                continue;
            }
            int hours = parseHoursFromShiftType(shift.getShiftType());
            totals.merge(shift.getEmployee().getId(), hours, Integer::sum);
        }
        return totals;
    }

    private int parseHoursFromShiftType(String shiftType) {
        if (shiftType == null) {
            return 8;
        }
        if (shiftType.contains("FULL_TIME") || shiftType.startsWith("SHIFT_")) {
            return 8;
        }
        if (shiftType.contains("PART_TIME_6") || shiftType.contains("10_16") || shiftType.contains("16_22")) {
            return 6;
        }
        if (shiftType.contains("PART_TIME_4") || shiftType.contains("10_14") || shiftType.contains("16_20")) {
            return 4;
        }
        return 8;
    }
}