package com.licenta.licentabackend.repository;

import com.licenta.licentabackend.domain.AbsenceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AbsenceRequestRepository extends JpaRepository<AbsenceRequest, Long> {

    // Check if there's already a pending/active request for a given shift
    Optional<AbsenceRequest> findByShiftIdAndStatusIn(Long shiftId, List<String> statuses);

    List<AbsenceRequest> findByRequestingEmployeeId(Long employeeId);
}
