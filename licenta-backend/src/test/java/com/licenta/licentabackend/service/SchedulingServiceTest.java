package com.licenta.licentabackend.service;

import com.licenta.licentabackend.repository.AbsenceRequestRepository;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.LeaveRequestRepository;
import com.licenta.licentabackend.repository.NotificationRepository;
import com.licenta.licentabackend.repository.ReplacementOfferRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import com.licenta.licentabackend.repository.StoreRepository;
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
    private StoreRepository storeRepository;

    @Mock
    private CsvReaderService csvReaderService;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private AbsenceRequestRepository absenceRequestRepository;

    @Mock
    private ReplacementOfferRepository replacementOfferRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void generateScheduleForMonthFailsWhenCsvPathMissing() {
        SchedulingService service = new SchedulingService(
                employeeRepository,
                shiftRepository,
            storeRepository,
                csvReaderService,
            leaveRequestRepository,
            absenceRequestRepository,
            replacementOfferRepository,
            notificationRepository,
                ""
        );

        assertThrows(IllegalArgumentException.class, () -> service.generateScheduleForMonth(2026, 5));
    }
}
