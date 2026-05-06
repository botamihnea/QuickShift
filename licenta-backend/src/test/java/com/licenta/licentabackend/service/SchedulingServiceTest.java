package com.licenta.licentabackend.service;

import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private CsvReaderService csvReaderService;

    @Test
    void generateScheduleForMonthFailsWhenCsvPathMissing() {
        SchedulingService service = new SchedulingService(
                employeeRepository,
                shiftRepository,
                csvReaderService,
                ""
        );

        assertThrows(IllegalArgumentException.class, () -> service.generateScheduleForMonth(2026, 5));
    }
}
