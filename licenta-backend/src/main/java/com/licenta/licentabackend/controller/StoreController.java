package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.dto.StoreDto;
import com.licenta.licentabackend.dto.UpdateBusyDayThresholdRequest;
import com.licenta.licentabackend.repository.StoreRepository;
import com.licenta.licentabackend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    public StoreController(StoreRepository storeRepository, UserRepository userRepository) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<StoreDto>> getAllStores() {
        List<StoreDto> stores = storeRepository.findAll().stream()
            .map(store -> new StoreDto(store.getId(), store.getStoreName(), store.getBusyDaySalesThreshold()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(stores);
    }

    @PutMapping("/threshold")
    public ResponseEntity<StoreDto> updateMyStoreThreshold(
            Authentication authentication,
            @RequestBody UpdateBusyDayThresholdRequest request
    ) {
        AppUser user = resolveCurrentUser(authentication);

        if (user.getStore() == null) {
            return ResponseEntity.badRequest().build();
        }

        if (request == null || request.busyDaySalesThreshold() == null || request.busyDaySalesThreshold() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        var store = user.getStore();
        store.setBusyDaySalesThreshold(request.busyDaySalesThreshold());
        var saved = storeRepository.save(store);

        return ResponseEntity.ok(new StoreDto(saved.getId(), saved.getStoreName(), saved.getBusyDaySalesThreshold()));
    }

    private AppUser resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("No authenticated user found.");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }
}
