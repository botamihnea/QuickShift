package com.licenta.licentabackend.repository;

import com.licenta.licentabackend.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Long> {

    // Spring citește numele metodei "findByDataTurei" și generează automat query-ul:
    // SELECT * FROM ture WHERE data_turei = ?
    List<Shift> findByShiftDate(LocalDate shiftDate);

    // Toate turele unui anumit angajat
    List<Shift> findByEmployeeId(Long employeeId);

    void deleteByEmployeeId(Long employeeId);

    List<Shift> findByEmployeeStoreId(Long storeId);

    List<Shift> findByShiftDateBetween(LocalDate startDate, LocalDate endDate);

    List<Shift> findByEmployeeStoreIdAndShiftDateBetween(Long storeId, LocalDate startDate, LocalDate endDate);

}