package com.licenta.licentabackend.repository;

import com.licenta.licentabackend.domain.ReplacementOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReplacementOfferRepository extends JpaRepository<ReplacementOffer, Long> {
    Optional<ReplacementOffer> findByAbsenceRequestIdAndStatus(Long absenceRequestId, String status);

    List<ReplacementOffer> findByAbsenceRequestId(Long absenceRequestId);

    void deleteByAbsenceRequestIdIn(List<Long> absenceRequestIds);
}
