package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Notification;
import com.licenta.licentabackend.dto.NotificationDto;
import com.licenta.licentabackend.repository.AbsenceRequestRepository;
import com.licenta.licentabackend.repository.LeaveRequestRepository;
import com.licenta.licentabackend.repository.NotificationRepository;
import com.licenta.licentabackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AbsenceRequestRepository absenceRequestRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public NotificationController(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            AbsenceRequestRepository absenceRequestRepository,
            LeaveRequestRepository leaveRequestRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.absenceRequestRepository = absenceRequestRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(Authentication authentication) {
        AppUser user = resolveCurrentUser(authentication);
        List<NotificationDto> result = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toDto)
                .toList();

        log.debug("Fetched {} notifications for user {}", result.size(), user.getEmail());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, Authentication authentication) {
        AppUser user = resolveCurrentUser(authentication);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found."));

        if (!notification.getRecipient().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        notification.setRead(true);
        notificationRepository.save(notification);
        log.debug("Notification {} marked as read by user {}", id, user.getEmail());
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

    private NotificationDto toDto(Notification notification) {
        Long storeId = notification.getStore() != null ? notification.getStore().getId() : null;
        String storeName = notification.getStore() != null ? notification.getStore().getStoreName() : null;
        String absenceReason = null;
        if (notification.getRelatedAbsenceRequestId() != null) {
            absenceReason = absenceRequestRepository.findById(notification.getRelatedAbsenceRequestId())
                    .map(absenceRequest -> absenceRequest.getReason())
                    .orElse(null);
        }
        String leaveReason = null;
        if (notification.getRelatedLeaveRequestId() != null) {
            leaveReason = leaveRequestRepository.findById(notification.getRelatedLeaveRequestId())
                    .map(leaveRequest -> leaveRequest.getReason())
                    .orElse(null);
        }

        return new NotificationDto(
                notification.getId(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.isRead(),
                storeId,
                storeName,
            notification.getRelatedAbsenceRequestId(),
            notification.getRelatedLeaveRequestId(),
            notification.getRelatedReplacementOfferId(),
            absenceReason,
            leaveReason
        );
    }
}