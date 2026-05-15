package com.licenta.licentabackend.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    // Non-null only for absence-report notifications sent to the manager
    @Column(name = "related_absence_request_id")
    private Long relatedAbsenceRequestId;

    // Non-null only for leave-request notifications sent to the manager
    @Column(name = "related_leave_request_id")
    private Long relatedLeaveRequestId;

    public Notification() {}

    public Notification(String message, AppUser recipient, Store store) {
        this.message = message;
        this.recipient = recipient;
        this.store = store;
        this.createdAt = LocalDateTime.now();
        this.isRead = false;
    }

    public Notification(String message, AppUser recipient, Store store, Long relatedAbsenceRequestId) {
        this(message, recipient, store);
        this.relatedAbsenceRequestId = relatedAbsenceRequestId;
    }

    public Notification(String message, AppUser recipient, Store store, Long relatedAbsenceRequestId, Long relatedLeaveRequestId) {
        this(message, recipient, store, relatedAbsenceRequestId);
        this.relatedLeaveRequestId = relatedLeaveRequestId;
    }

    public Long getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        this.isRead = read;
    }

    public AppUser getRecipient() {
        return recipient;
    }

    public void setRecipient(AppUser recipient) {
        this.recipient = recipient;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Long getRelatedAbsenceRequestId() {
        return relatedAbsenceRequestId;
    }

    public void setRelatedAbsenceRequestId(Long relatedAbsenceRequestId) {
        this.relatedAbsenceRequestId = relatedAbsenceRequestId;
    }

    public Long getRelatedLeaveRequestId() {
        return relatedLeaveRequestId;
    }

    public void setRelatedLeaveRequestId(Long relatedLeaveRequestId) {
        this.relatedLeaveRequestId = relatedLeaveRequestId;
    }
}