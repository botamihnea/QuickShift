package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.Store;
import com.licenta.licentabackend.dto.CreateStoreRequest;
import com.licenta.licentabackend.dto.StoreDto;
import com.licenta.licentabackend.repository.StoreRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final StoreRepository storeRepository;

    public AdminController(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    // Endpoint: POST /api/admin/stores
    @PostMapping("/stores")
    public ResponseEntity<StoreDto> createNewStore(@RequestBody CreateStoreRequest request) {

        if (storeRepository.findByStoreName(request.storeName()).isPresent()) {
            throw new IllegalArgumentException("A store with this name already exists.");
        }


        Store newStore = new Store(
                request.storeName(),
                request.address(),
                request.busyDaySalesThreshold()
        );

        Store savedStore = storeRepository.save(newStore);

        StoreDto responseDto = new StoreDto(savedStore.getId(), savedStore.getStoreName());

        return ResponseEntity.ok(responseDto);
    }

}
