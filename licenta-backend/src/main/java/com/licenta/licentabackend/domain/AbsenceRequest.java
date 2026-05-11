package com.licenta.licentabackend.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "absence_requests")
public class AbsenceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requesting_employee_id", nullable = false)
    private Employee requestingEmployee;

    // "PENDING" -> manager notified, not yet acted on
    // "COVERED" -> replacement found and inserted
    // "UNRESOLVABLE" -> acknowledged but no replacement available
    @Column(nullable = false)
    private String status;

    @Column
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public AbsenceRequest() {
    }

    public AbsenceRequest(Shift shift, Employee requestingEmployee, String reason) {
        this.shift = shift;
        this.requestingEmployee = requestingEmployee;
        this.reason = reason;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Shift getShift() {
        return shift;
    }

    public void setShift(Shift shift) {
        this.shift = shift;
    }

    public Employee getRequestingEmployee() {
        return requestingEmployee;
    }

    public void setRequestingEmployee(Employee requestingEmployee) {
        this.requestingEmployee = requestingEmployee;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
