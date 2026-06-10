package com.licenta.licentabackend.service;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Notification;
import com.licenta.licentabackend.domain.Shift;
import com.licenta.licentabackend.domain.Store;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.LeaveRequestRepository;
import com.licenta.licentabackend.repository.NotificationRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftManagementServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void createManualShiftRejectsEmployeeFromOtherStore() {
        Store store = new Store("Central", "Main", 900.0);
        store.setId(1L);

        Store otherStore = new Store("Other", "Side", 800.0);
        otherStore.setId(2L);

        Employee employee = new Employee();
        employee.setId(10L);
        employee.setStore(otherStore);

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        ShiftManagementService service = new ShiftManagementService(
                employeeRepository,
                shiftRepository,
                leaveRequestRepository,
                notificationRepository
        );

        assertThrows(IllegalArgumentException.class, () -> service.createManualShift(
                1L,
                10L,
                LocalDate.now().plusDays(10),
                "SHIFT_1_10_18"
        ));
    }

    @Test
    void createManualShiftRejectsAlreadyScheduledEmployee() {
        Store store = new Store("Central", "Main", 900.0);
        store.setId(1L);

        Employee employee = new Employee();
        employee.setId(10L);
        employee.setStore(store);

        Shift existingShift = new Shift();
        existingShift.setEmployee(employee);
        existingShift.setShiftDate(LocalDate.now().plusDays(12));
        existingShift.setShiftType("SHIFT_1_10_18");

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(shiftRepository.findByEmployeeIdAndShiftDateBetween(eq(10L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(existingShift));

        ShiftManagementService service = new ShiftManagementService(
                employeeRepository,
                shiftRepository,
                leaveRequestRepository,
                notificationRepository
        );
        ShiftManagementService spyService = spy(service);
        doReturn(List.of(employee)).when(spyService).getEligibleEmployees(
                eq(1L),
                eq(existingShift.getShiftDate()),
                eq("SHIFT_1_10_18")
        );

        assertThrows(IllegalArgumentException.class, () -> spyService.createManualShift(
                1L,
                10L,
                existingShift.getShiftDate(),
                "SHIFT_1_10_18"
        ));
    }

    @Test
    void deleteShiftRemovesAndNotifiesEmployee() {
        Store store = new Store("Central", "Main", 900.0);
        store.setId(5L);

        Employee employee = new Employee();
        employee.setId(77L);
        employee.setStore(store);
        employee.setAppUser(org.mockito.Mockito.mock(AppUser.class));

        Shift shift = new Shift();
        shift.setId(100L);
        shift.setEmployee(employee);
        shift.setShiftDate(LocalDate.now().plusDays(4));
        shift.setShiftType("SHIFT_1_10_18");

        when(shiftRepository.findById(100L)).thenReturn(Optional.of(shift));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShiftManagementService service = new ShiftManagementService(
                employeeRepository,
                shiftRepository,
                leaveRequestRepository,
                notificationRepository
        );

        service.deleteShift(5L, 100L);

        verify(shiftRepository).delete(shift);
        verify(notificationRepository).save(any(Notification.class));
    }
}
