package com.licenta.licentabackend.repository;

import com.licenta.licentabackend.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    // DOAR prin extensia JpaRepository, exista gratuit următoarele metode:
    // save(Angajat a) -> face INSERT sau UPDATE
    // findAll() -> face SELECT * FROM angajati
    // findById(Lodng id) -> face SELECT * FROM angajati WHERE id = ?
    // deleteById(Long id) -> face DELETE FROM angajati WHERE id = ?
    List<Employee> findByStoreId(Long storeId);
}
