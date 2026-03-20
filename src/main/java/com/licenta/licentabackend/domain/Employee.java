package com.licenta.licentabackend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "contract_type", nullable = false)
    private String contractType; // Ex: "FULL_TIME_8H", "PART_TIME_4H"

    @Column(name = "role", nullable = false)
    private String role; // Ex: "MANAGER", "FLORIST"

    @Column(name = "remaining_leave_days")
    private Integer remainingLeaveDays;

    @Column(name = "holiday_recovery_hours")
    private Integer holidayRecoveryHours;

    @Column(name = "shift_preference")
    private String shiftPreference; // Ex: "MORNING", "EVENING"

    // --- Constructori ---
    public Employee() {

    }

    // --- Getteri și Setteri ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getContractType() {
        return contractType;
    }

    public void setContractType(String contractType) {
        this.contractType = contractType;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getRemainingLeaveDays() {
        return remainingLeaveDays;
    }

    public void setRemainingLeaveDays(Integer remainingLeaveDays) {
        this.remainingLeaveDays = remainingLeaveDays;
    }

    public Integer getHolidayRecoveryHours() {
        return holidayRecoveryHours;
    }

    public void setHolidayRecoveryHours(Integer holidayRecoveryHours) {
        this.holidayRecoveryHours = holidayRecoveryHours;
    }

    public String getShiftPreference() {
        return shiftPreference;
    }

    public void setShiftPreference(String shiftPreference) {
        this.shiftPreference = shiftPreference;
    }
}