package com.licenta.licentabackend.repository;

import com.licenta.licentabackend.domain.Tura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TuraRepository extends JpaRepository<Tura, Long> {

    // Spring citește numele metodei "findByDataTurei" și generează automat query-ul:
    // SELECT * FROM ture WHERE data_turei = ?
    List<Tura> findByDataTurei(LocalDate dataTurei);

    // Toate turele unui anumit angajat
    List<Tura> findByAngajatId(Long angajatId);
}