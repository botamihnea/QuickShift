package com.licenta.licentabackend.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "shifts")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "shift_type", nullable = false)
    private String shiftType; // Ex: "SHIFT_1_10_18", "PART_TIME_16_20", "PART_TIME_16_22"

    // "SCHEDULED" (normal) | "ABSENT" (employee called out) | "REPLACEMENT" (cover shift)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'SCHEDULED'")
    private String status = "SCHEDULED";

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // --- Constructori ---
    public Shift() {

    }

    // --- Getteri și Setteri ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public void setShiftDate(LocalDate shiftDate) {
        this.shiftDate = shiftDate;
    }

    public String getShiftType() {
        return shiftType;
    }

    public void setShiftType(String shiftType) {
        this.shiftType = shiftType;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}