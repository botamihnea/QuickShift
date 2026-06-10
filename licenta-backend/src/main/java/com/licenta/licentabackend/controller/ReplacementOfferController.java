package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.repository.UserRepository;
import com.licenta.licentabackend.service.AbsenceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/replacement-offers")
@CrossOrigin(origins = "*")
public class ReplacementOfferController {

    private final AbsenceService absenceService;
    private final UserRepository userRepository;

    public ReplacementOfferController(AbsenceService absenceService, UserRepository userRepository) {
        this.absenceService = absenceService;
        this.userRepository = userRepository;
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveOffer(
            @PathVariable Long id,
            Authentication authentication
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);

        if (currentUser.getRole() != Role.EMPLOYEE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only employees can approve replacement offers.");
        }

        try {
            absenceService.approveReplacementOffer(id, currentUser);
            return ResponseEntity.ok("Replacement offer accepted.");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/{id}/deny")
    public ResponseEntity<?> denyOffer(
            @PathVariable Long id,
            Authentication authentication
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);

        if (currentUser.getRole() != Role.EMPLOYEE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only employees can deny replacement offers.");
        }

        try {
            absenceService.denyReplacementOffer(id, currentUser);
            return ResponseEntity.ok("Replacement offer declined.");
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
