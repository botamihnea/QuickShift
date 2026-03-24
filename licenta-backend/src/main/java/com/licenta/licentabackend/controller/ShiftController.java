package com.licenta.licentabackend.controller;

import com.licenta.licentabackend.domain.Shift;
import com.licenta.licentabackend.dto.GenerateScheduleRequestDto;
import com.licenta.licentabackend.dto.GenerateScheduleResponseDto;
import com.licenta.licentabackend.exceptions.FailedReadingException;
import com.licenta.licentabackend.exceptions.NoEmployeesException;
import com.licenta.licentabackend.repository.ShiftRepository;
import com.licenta.licentabackend.service.SchedulingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@CrossOrigin(origins = "*")
public class ShiftController {
    private final ShiftRepository shiftRepository;
    private final SchedulingService schedulingService;

    public ShiftController(ShiftRepository shiftRepository, SchedulingService schedulingService) {
        this.shiftRepository = shiftRepository;
        this.schedulingService = schedulingService;
    }

    @GetMapping
    public ResponseEntity<List<Shift>> getAllShifts() {
        List<Shift> shifts = shiftRepository.findAll();

        if (shifts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(shifts);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateShifts(@RequestBody(required = false) GenerateScheduleRequestDto requestDto) {
        try {
            Integer year = requestDto != null ? requestDto.year() : null;
            Integer month = requestDto != null ? requestDto.month() : null;

            GenerateScheduleResponseDto response = schedulingService.generateScheduleForMonth(year, month);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (NoEmployeesException | FailedReadingException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
        }
    }

}
