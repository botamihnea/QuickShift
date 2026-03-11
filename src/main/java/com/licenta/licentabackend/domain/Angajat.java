package com.licenta.licentabackend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "angajati")
public class Angajat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nume_complet", nullable = false)
    private String numeComplet;

    @Column(name = "rol", nullable = false)
    private String rol;

    @Column(name = "tip_contract", nullable = false)
    private String tipContract; // Ex: "FULL_TIME", "PART_TIME"

    @Column(name = "zile_concediu_ramase")
    private Integer zileConcediuRamase;

    @Column(name = "ore_lucrate_luna_curenta")
    private Integer oreLucrateLunaCurenta;

    @Column(name = "ore_recuperare_sarbatori")
    private Integer oreRecuperareSarbatori;

    @Column(name = "preferinta_tura")
    private String preferintaTura; // Ex: "DIMINEATA", "SEARA"

    // --- Constructori ---

    public Angajat() {
        // Constructor gol obligatoriu pentru Hibernate
    }

    // --- Gettere și Settere ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeComplet() {
        return numeComplet;
    }

    public void setNumeComplet(String numeComplet) {
        this.numeComplet = numeComplet;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getRole() {
        return rol;
    }

    public String getTipContract() {
        return tipContract;
    }

    public void setTipContract(String tipContract) {
        this.tipContract = tipContract;
    }

    public Integer getZileConcediuRamase() {
        return zileConcediuRamase;
    }

    public void setZileConcediuRamase(Integer zileConcediuRamase) {
        this.zileConcediuRamase = zileConcediuRamase;
    }

    public Integer getOreLucrateLunaCurenta() {
        return oreLucrateLunaCurenta;
    }

    public void setOreLucrateLunaCurenta(Integer oreLucrateLunaCurenta) {
        this.oreLucrateLunaCurenta = oreLucrateLunaCurenta;
    }

    public Integer getOreRecuperareSarbatori() {
        return oreRecuperareSarbatori;
    }

    public void setOreRecuperareSarbatori(Integer oreRecuperareSarbatori) {
        this.oreRecuperareSarbatori = oreRecuperareSarbatori;
    }

    public String getPreferintaTura() {
        return preferintaTura;
    }

    public void setPreferintaTura(String preferintaTura) {
        this.preferintaTura = preferintaTura;
    }
}
