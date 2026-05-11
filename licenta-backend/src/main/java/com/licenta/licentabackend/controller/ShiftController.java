package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.dto.GenerateScheduleRequestDto;
import com.licenta.licentabackend.dto.GenerateScheduleResponseDto;
import com.licenta.licentabackend.dto.ShiftDto;
import com.licenta.licentabackend.exceptions.FailedReadingException;
import com.licenta.licentabackend.exceptions.NoEmployeesException;
import com.licenta.licentabackend.repository.ShiftRepository;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.UserRepository;
import com.licenta.licentabackend.service.SchedulingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@CrossOrigin(origins = "*")
public class ShiftController {
    private final ShiftRepository shiftRepository;
    private final SchedulingService schedulingService;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public ShiftController(
            ShiftRepository shiftRepository,
            SchedulingService schedulingService,
            UserRepository userRepository,
            EmployeeRepository employeeRepository
    ) {
        this.shiftRepository = shiftRepository;
        this.schedulingService = schedulingService;
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
            Integer year = requestDto != null ? requestDto.year() : null;
            Integer month = requestDto != null ? requestDto.month() : null;
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
