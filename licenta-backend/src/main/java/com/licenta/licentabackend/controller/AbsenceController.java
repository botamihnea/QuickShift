package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.dto.AbsenceReportRequest;
import com.licenta.licentabackend.dto.AcknowledgeAbsenceResponse;
import com.licenta.licentabackend.repository.UserRepository;
import com.licenta.licentabackend.service.AbsenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AbsenceController {

    private static final Logger log = LoggerFactory.getLogger(AbsenceController.class);

    private final AbsenceService absenceService;
    private final UserRepository userRepository;

    public AbsenceController(AbsenceService absenceService, UserRepository userRepository) {
        this.absenceService = absenceService;
        this.userRepository = userRepository;
    }

    /**
     * POST /api/shifts/{shiftId}/report-absence
     * Employee reports that they cannot attend a future shift.
     * Sends a notification to the store manager.
     */
    @PostMapping("/shifts/{shiftId}/report-absence")
    public ResponseEntity<?> reportAbsence(
            @PathVariable Long shiftId,
            @RequestBody(required = false) AbsenceReportRequest request,
            Authentication authentication
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);

        if (currentUser.getRole() != Role.EMPLOYEE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only employees can report absences.");
        }

        try {
            String reason = request != null ? request.reason() : null;
            absenceService.reportAbsence(shiftId, reason, currentUser);
            log.info("Absence reported: shift={}, user={}", shiftId, currentUser.getEmail());
            return ResponseEntity.ok("Your manager has been notified. We will find a replacement for your shift.");
        } catch (IllegalArgumentException ex) {
            log.warn("Absence report failed for shift {}: {}", shiftId, ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    /**
     * POST /api/absence-requests/{id}/acknowledge
     * Manager acknowledges the absence and triggers replacement-finding logic.
     * The main schedule is updated immediately (new REPLACEMENT shift inserted).
     */
    @PostMapping("/absence-requests/{id}/acknowledge")
    public ResponseEntity<?> acknowledgeAbsence(
            @PathVariable Long id,
            Authentication authentication
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);

        if (currentUser.getRole() != Role.MANAGER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only managers can acknowledge absences.");
        }

        try {
            AcknowledgeAbsenceResponse result = absenceService.acknowledgeAbsence(id, currentUser);
            log.info("Absence acknowledged: request={}, replacementFound={}", id, result.replacementFound());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    /**
     * POST /api/absence-requests/{id}/find-replacement
     * Manager triggers another replacement offer.
     */
    @PostMapping("/absence-requests/{id}/find-replacement")
    public ResponseEntity<?> findAnotherReplacement(
            @PathVariable Long id,
            Authentication authentication
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);

        if (currentUser.getRole() != Role.MANAGER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only managers can find replacements.");
        }

        try {
            absenceService.findAnotherReplacement(id, currentUser);
            log.info("Replacement search triggered: request={}", id);
            return ResponseEntity.ok("Replacement search triggered.");
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
}
