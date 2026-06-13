package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.dto.LeaveRequestCreateRequest;
import com.licenta.licentabackend.dto.LeaveRequestDecisionRequest;
import com.licenta.licentabackend.dto.LeaveRequestResponse;
import com.licenta.licentabackend.repository.UserRepository;
import com.licenta.licentabackend.service.LeaveRequestService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leave-requests")
@CrossOrigin(origins = "*")
public class LeaveRequestController {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestController.class);

    private final LeaveRequestService leaveRequestService;
    private final UserRepository userRepository;

    public LeaveRequestController(LeaveRequestService leaveRequestService, UserRepository userRepository) {
        this.leaveRequestService = leaveRequestService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> createLeaveRequest(
            Authentication authentication,
            @Valid @RequestBody LeaveRequestCreateRequest request
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);
        try {
            LeaveRequestResponse response = leaveRequestService.requestLeave(currentUser, request);
            log.info("Leave request created via API: id={}, user={}", response.id(), authentication.getName());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveLeave(
            @PathVariable Long id,
            Authentication authentication
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);
        try {
            LeaveRequestResponse response = leaveRequestService.approveLeave(id, currentUser);
            log.info("Leave approved via API: id={}, manager={}", id, authentication.getName());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/{id}/deny")
    public ResponseEntity<?> denyLeave(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody(required = false) LeaveRequestDecisionRequest request
    ) {
        AppUser currentUser = resolveCurrentUser(authentication);
        try {
            LeaveRequestResponse response = leaveRequestService.denyLeave(id, currentUser, request);
            log.info("Leave denied via API: id={}, manager={}", id, authentication.getName());
            return ResponseEntity.ok(response);
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
