package com.licenta.licentabackend.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "ture")
public class Tura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Data exactă a turei (ex: 2026-03-01)
    @Column(name = "data_turei", nullable = false)
    private LocalDate dataTurei;

    // Aici salvăm tipul turei.
    // Conform design-ului , va fi: "TURA_1_10_18", "TURA_2_14_22" sau "PART_TIME_16_20"
    @Column(name = "tip_tura", nullable = false)
    private String tipTura;

    // Aici facem legătura (Relația) cu tabelul Angajati - foreign key pentru id
    @ManyToOne
    @JoinColumn(name = "angajat_id", nullable = false)
    private Angajat angajat;

    // --- Constructori ---

    public Tura() {
        // Constructor gol obligatoriu pentru Hibernate
    }

    // --- Getteri și Setteri ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDataTurei() {
        return dataTurei;
    }

    public void setDataTurei(LocalDate dataTurei) {
        this.dataTurei = dataTurei;
    }

    public String getTipTura() {
        return tipTura;
    }

    public void setTipTura(String tipTura) {
        this.tipTura = tipTura;
    }

    public Angajat getAngajat() {
        return angajat;
    }

    public void setAngajat(Angajat angajat) {
        this.angajat = angajat;
    }
}
