package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.dto.EligibleEmployeeDto;
import com.licenta.licentabackend.dto.GenerateScheduleRequestDto;
import com.licenta.licentabackend.dto.GenerateScheduleResponseDto;
import com.licenta.licentabackend.dto.ManualShiftRequest;
import com.licenta.licentabackend.dto.ShiftDto;
import com.licenta.licentabackend.exceptions.FailedReadingException;
import com.licenta.licentabackend.exceptions.NoEmployeesException;
import com.licenta.licentabackend.repository.ShiftRepository;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.UserRepository;
import com.licenta.licentabackend.service.SchedulingService;
import com.licenta.licentabackend.service.ShiftManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@CrossOrigin(origins = "*")
public class ShiftController {
    private final ShiftRepository shiftRepository;
    private final SchedulingService schedulingService;
    private final ShiftManagementService shiftManagementService;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public ShiftController(
            ShiftRepository shiftRepository,
            SchedulingService schedulingService,
            ShiftManagementService shiftManagementService,
            UserRepository userRepository,
            EmployeeRepository employeeRepository
    ) {
        this.shiftRepository = shiftRepository;
        this.schedulingService = schedulingService;
        this.shiftManagementService = shiftManagementService;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping
    public ResponseEntity<List<ShiftDto>> getAllShifts(
            Authentication authentication,
            @RequestParam(required = false) Long storeId
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);

        List<com.licenta.licentabackend.domain.Shift> shifts;
        if (currentUser.getRole() == Role.ADMIN) {
            shifts = storeId == null
                    ? shiftRepository.findAll()
                    : shiftRepository.findByEmployeeStoreId(storeId);
        } else {
            shifts = shiftRepository.findByEmployeeStoreId(resolveStoreId(currentUser));
        }

        if (shifts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        List<ShiftDto> result = shifts.stream()
                .map(this::toDto)
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ShiftDto>> getMyShifts(Authentication authentication) {
        AppUser currentUser = resolveCurrentUser(authentication);
        if (currentUser.getRole() != Role.EMPLOYEE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long employeeId = employeeRepository.findByAppUserId(currentUser.getId())
                .map(employee -> employee.getId())
                .orElse(null);
        if (employeeId == null) {
            return ResponseEntity.noContent().build();
        }

        List<com.licenta.licentabackend.domain.Shift> shifts = shiftRepository.findByEmployeeId(employeeId);
        if (shifts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        List<ShiftDto> result = shifts.stream()
                .map(this::toDto)
                .toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateShifts(
            @RequestBody(required = false) GenerateScheduleRequestDto requestDto,
            Authentication authentication
    ) {
        try {
            AppUser currentUser = resolveCurrentUser(authentication);
            if (currentUser.getRole() == Role.EMPLOYEE) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers and admins can generate shifts.");
            }
            Integer year = null;
            Integer month = null;
            /*
            Integer year = requestDto != null ? requestDto.year() : null;
            Integer month = requestDto != null ? requestDto.month() : null;
            */
            Long targetStoreId;

            if (currentUser.getRole() == Role.ADMIN) {
                Long requestedStoreId = requestDto != null ? requestDto.storeId() : null;
                if (requestedStoreId == null) {
                    return ResponseEntity.badRequest().body("Store is required for admin schedule generation.");
                }
                targetStoreId = requestedStoreId;
            } else {
                targetStoreId = resolveStoreId(currentUser);
            }

            GenerateScheduleResponseDto response = schedulingService.generateScheduleForMonth(
                    year,
                    month,
                    targetStoreId
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (NoEmployeesException | FailedReadingException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
        }
    }

    @GetMapping("/eligible")
    public ResponseEntity<?> getEligibleEmployees(
            Authentication authentication,
            @RequestParam LocalDate date,
            @RequestParam String shiftType,
            @RequestParam(required = false) Long storeId
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);
        if (currentUser.getRole() == Role.EMPLOYEE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers and admins can access eligibility.");
        }

        Long targetStoreId;
        if (currentUser.getRole() == Role.ADMIN) {
            if (storeId == null) {
                return ResponseEntity.badRequest().body("Store is required for admin eligibility lookup.");
            }
            targetStoreId = storeId;
        } else {
            targetStoreId = resolveStoreId(currentUser);
        }

        List<EligibleEmployeeDto> result = shiftManagementService
                .getEligibleEmployees(targetStoreId, date, shiftType)
                .stream()
                .map(employee -> new EligibleEmployeeDto(
                        employee.getId(),
                        employee.getFullName(),
                        employee.getContractType(),
                        employee.getShiftPreference()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/manual")
    public ResponseEntity<?> createManualShift(
            @RequestBody ManualShiftRequest request,
            Authentication authentication
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);
        if (currentUser.getRole() == Role.EMPLOYEE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers and admins can add shifts.");
        }

        if (request == null || request.date() == null || request.shiftType() == null || request.employeeId() == null) {
            return ResponseEntity.badRequest().body("Date, shift type, and employee are required.");
        }

        Long targetStoreId = currentUser.getRole() == Role.ADMIN
            ? request.storeId()
            : resolveStoreId(currentUser);
        if (currentUser.getRole() == Role.ADMIN && targetStoreId == null) {
            return ResponseEntity.badRequest().body("Store is required for admin shift creation.");
        }

        try {
            ShiftDto dto = toDto(shiftManagementService.createManualShift(targetStoreId, request.employeeId(), request.date(), request.shiftType()));
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
        public ResponseEntity<?> deleteShift(
            @PathVariable Long id,
            @RequestParam(required = false) Long storeId,
            Authentication authentication
        ) {
        AppUser currentUser = resolveCurrentUser(authentication);
        if (currentUser.getRole() == Role.EMPLOYEE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers and admins can delete shifts.");
        }

        Long targetStoreId = currentUser.getRole() == Role.ADMIN
            ? storeId
            : resolveStoreId(currentUser);
        if (targetStoreId == null) {
            return ResponseEntity.badRequest().body("Store is required for shift deletion.");
        }

        try {
            shiftManagementService.deleteShift(targetStoreId, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    private AppUser resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("No authenticated user found.");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }

    private Long resolveStoreId(AppUser currentUser) {
        if (currentUser.getStore() == null || currentUser.getStore().getId() == null) {
            throw new IllegalArgumentException("Your account is not assigned to a store.");
        }

        return currentUser.getStore().getId();
    }

    private ShiftDto toDto(com.licenta.licentabackend.domain.Shift shift) {
        return new ShiftDto(
                shift.getId(),
                shift.getShiftDate(),
                shift.getShiftType(),
                shift.getStatus(),
                new ShiftDto.EmployeeSummary(
                        shift.getEmployee().getId(),
                        shift.getEmployee().getFullName()
                )
        );
    }

}
