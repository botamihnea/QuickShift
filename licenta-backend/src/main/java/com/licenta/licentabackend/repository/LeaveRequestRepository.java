package com.licenta.licentabackend.repository;

import com.licenta.licentabackend.domain.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByStatusAndRequestingEmployeeStoreIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            String status,
            Long storeId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<LeaveRequest> findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            String status,
            LocalDate startDate,
            LocalDate endDate
    );

    boolean existsByRequestingEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId,
            List<String> statuses,
            LocalDate startDate,
            LocalDate endDate
    );
}
