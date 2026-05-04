package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.domain.Shift;
import com.licenta.licentabackend.dto.GenerateScheduleRequestDto;
import com.licenta.licentabackend.dto.GenerateScheduleResponseDto;
import com.licenta.licentabackend.exceptions.FailedReadingException;
import com.licenta.licentabackend.exceptions.NoEmployeesException;
import com.licenta.licentabackend.repository.ShiftRepository;
import com.licenta.licentabackend.repository.UserRepository;
import com.licenta.licentabackend.service.SchedulingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public ShiftController(
            ShiftRepository shiftRepository,
            SchedulingService schedulingService,
            UserRepository userRepository
    ) {
        this.shiftRepository = shiftRepository;
        this.schedulingService = schedulingService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<Shift>> getAllShifts(Authentication authentication) {
        AppUser currentUser = resolveCurrentUser(authentication);

        List<Shift> shifts = currentUser.getRole() == Role.ADMIN
                ? shiftRepository.findAll()
                : shiftRepository.findByEmployeeStoreId(resolveStoreId(currentUser));

        if (shifts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(shifts);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateShifts(
            @RequestBody(required = false) GenerateScheduleRequestDto requestDto,
            Authentication authentication
    ) {
        try {
            AppUser currentUser = resolveCurrentUser(authentication);
            Integer year = requestDto != null ? requestDto.year() : null;
            Integer month = requestDto != null ? requestDto.month() : null;
            Long targetStoreId = currentUser.getRole() == Role.ADMIN ? null : resolveStoreId(currentUser);

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

}
