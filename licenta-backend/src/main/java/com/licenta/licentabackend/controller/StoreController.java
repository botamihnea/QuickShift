package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.dto.StoreDto;
import com.licenta.licentabackend.repository.StoreRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreRepository storeRepository;

    public StoreController(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @GetMapping
    public ResponseEntity<List<StoreDto>> getAllStores() {
        List<StoreDto> stores = storeRepository.findAll().stream()
                .map(store -> new StoreDto(store.getId(), store.getStoreName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(stores);
    }
}
