package com.licenta.licentabackend.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requesting_employee_id", nullable = false)
    private Employee requestingEmployee;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "requested_days", nullable = false)
    private Integer requestedDays;

    @Column(name = "deductible_days")
    private Integer deductibleDays;

    // "PENDING" -> manager notified, not yet acted on
    // "APPROVED" -> approved by manager
    // "DENIED" -> denied by manager
    @Column(nullable = false)
    private String status;

    @Column
    private String reason;

    @Column(name = "manager_response")
    private String managerResponse;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public LeaveRequest() {
    }

    public LeaveRequest(Employee requestingEmployee, LocalDate startDate, LocalDate endDate, Integer requestedDays, Integer deductibleDays, String reason) {
        this.requestingEmployee = requestingEmployee;
        this.startDate = startDate;
        this.endDate = endDate;
        this.requestedDays = requestedDays;
        this.deductibleDays = deductibleDays;
        this.reason = reason;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Employee getRequestingEmployee() {
        return requestingEmployee;
    }

    public void setRequestingEmployee(Employee requestingEmployee) {
        this.requestingEmployee = requestingEmployee;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getRequestedDays() {
        return requestedDays;
    }

    public void setRequestedDays(Integer requestedDays) {
        this.requestedDays = requestedDays;
    }

    public Integer getDeductibleDays() {
        return deductibleDays;
    }

    public void setDeductibleDays(Integer deductibleDays) {
        this.deductibleDays = deductibleDays;
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

    public String getManagerResponse() {
        return managerResponse;
    }

    public void setManagerResponse(String managerResponse) {
        this.managerResponse = managerResponse;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }
}
