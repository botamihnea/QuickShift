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

    @Column(name = "remaining_leave_days")
    private Integer remainingLeaveDays;

    @Column(name = "holiday_recovery_hours")
    private Integer holidayRecoveryHours;

    @Column(name = "shift_preference")
    private String shiftPreference;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    @OneToOne
    @JoinColumn(name = "user_id")
    private AppUser appUser;

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
        this.contractType = contractType; //FULL_TIME_8H, PART_TIME_4H, PART_TIME_6H
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

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public void setAppUser(AppUser appUser) {
        this.appUser = appUser;
    }
}