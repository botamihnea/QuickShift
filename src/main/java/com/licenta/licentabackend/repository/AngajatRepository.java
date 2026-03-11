package com.licenta.licentabackend.repository;

import com.licenta.licentabackend.domain.Angajat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AngajatRepository extends JpaRepository<Angajat, Long> {
    // DOAR prin extensia JpaRepository, exista gratuit următoarele metode:
    // save(Angajat a) -> face INSERT sau UPDATE
    // findAll() -> face SELECT * FROM angajati
    // findById(Long id) -> face SELECT * FROM angajati WHERE id = ?
    // deleteById(Long id) -> face DELETE FROM angajati WHERE id = ?
}
