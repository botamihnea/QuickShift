package com.licenta.licentabackend.repository;

import com.licenta.licentabackend.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByStoreName(String storeName);

    @Query("SELECT s FROM Store s WHERE s.id NOT IN (SELECT u.store.id FROM AppUser u WHERE u.role = 'MANAGER' AND u.store IS NOT NULL)")
    List<Store> findStoresWithNoManager();
}
