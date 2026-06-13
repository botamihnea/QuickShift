package com.licenta.licentabackend.service;

import com.licenta.licentabackend.domain.*;
import com.licenta.licentabackend.dto.AcknowledgeAbsenceResponse;
import com.licenta.licentabackend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbsenceServiceTest {

    @Mock
    private AbsenceRequestRepository absenceRequestRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private ReplacementOfferRepository replacementOfferRepository;

    @InjectMocks
    private AbsenceService absenceService;

    // -----------------------------------------------------------------------
    // reportAbsence — happy path
    // -----------------------------------------------------------------------

    @Test
    void reportAbsenceCreatesRequestAndNotifiesManager() {
        Store store = new Store("Central", "Main", 900.0);
        store.setId(10L);

        AppUser employeeUser = mock(AppUser.class);
        when(employeeUser.getId()).thenReturn(5L);

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setFullName("Ana Popescu");
        employee.setStore(store);
        employee.setAppUser(employeeUser);

        Shift shift = new Shift();
        shift.setId(200L);
        shift.setEmployee(employee);
        shift.setShiftDate(LocalDate.now().plusDays(3));
        shift.setShiftType("SHIFT_1_10_18");
        shift.setStatus("SCHEDULED");

        AppUser manager = mock(AppUser.class);

        when(employeeRepository.findByAppUserId(5L)).thenReturn(Optional.of(employee));
        when(shiftRepository.findById(200L)).thenReturn(Optional.of(shift));
        when(absenceRequestRepository.findByShiftIdAndStatusIn(eq(200L), anyList()))
                .thenReturn(Optional.empty());
        when(absenceRequestRepository.save(any(AbsenceRequest.class)))
                .thenAnswer(inv -> {
                    AbsenceRequest req = inv.getArgument(0);
                    // Simulate DB setting an ID
                    try {
                        var field = AbsenceRequest.class.getDeclaredField("id");
                        field.setAccessible(true);
                        field.set(req, 1L);
                    } catch (Exception ignored) {}
                    return req;
                });
        when(userRepository.findByStoreIdAndRole(10L, Role.MANAGER))
                .thenReturn(List.of(manager));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        absenceService.reportAbsence(200L, "sick", employeeUser);

        verify(absenceRequestRepository).save(any(AbsenceRequest.class));

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notifCaptor.capture());
        String message = notifCaptor.getValue().getMessage();
        assertTrue(message.contains("[ABSENCE REQUEST]"));
        assertTrue(message.contains("Ana Popescu"));
    }

    // -----------------------------------------------------------------------
    // reportAbsence — rejects past shifts
    // -----------------------------------------------------------------------

    @Test
    void reportAbsenceRejectsPastShift() {
        AppUser employeeUser = mock(AppUser.class);
        when(employeeUser.getId()).thenReturn(5L);

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setStore(new Store("S", "A", 1.0));

        Shift shift = new Shift();
        shift.setId(200L);
        shift.setEmployee(employee);
        shift.setShiftDate(LocalDate.now().minusDays(1)); // in the past
        shift.setShiftType("SHIFT_1_10_18");
        shift.setStatus("SCHEDULED");

        when(employeeRepository.findByAppUserId(5L)).thenReturn(Optional.of(employee));
        when(shiftRepository.findById(200L)).thenReturn(Optional.of(shift));

        assertThrows(IllegalArgumentException.class,
                () -> absenceService.reportAbsence(200L, "sick", employeeUser));
    }

    // -----------------------------------------------------------------------
    // reportAbsence — rejects already-processed shift
    // -----------------------------------------------------------------------

    @Test
    void reportAbsenceRejectsAlreadyProcessedShift() {
        AppUser employeeUser = mock(AppUser.class);
        when(employeeUser.getId()).thenReturn(5L);

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setStore(new Store("S", "A", 1.0));

        Shift shift = new Shift();
        shift.setId(200L);
        shift.setEmployee(employee);
        shift.setShiftDate(LocalDate.now().plusDays(5));
        shift.setShiftType("SHIFT_1_10_18");
        shift.setStatus("ABSENT"); // already processed

        when(employeeRepository.findByAppUserId(5L)).thenReturn(Optional.of(employee));
        when(shiftRepository.findById(200L)).thenReturn(Optional.of(shift));

        assertThrows(IllegalArgumentException.class,
                () -> absenceService.reportAbsence(200L, "sick", employeeUser));
    }

    // -----------------------------------------------------------------------
    // reportAbsence — rejects duplicate pending request
    // -----------------------------------------------------------------------

    @Test
    void reportAbsenceRejectsDuplicatePendingRequest() {
        AppUser employeeUser = mock(AppUser.class);
        when(employeeUser.getId()).thenReturn(5L);

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setStore(new Store("S", "A", 1.0));

        Shift shift = new Shift();
        shift.setId(200L);
        shift.setEmployee(employee);
        shift.setShiftDate(LocalDate.now().plusDays(5));
        shift.setShiftType("SHIFT_1_10_18");
        shift.setStatus("SCHEDULED");

        AbsenceRequest existing = mock(AbsenceRequest.class);

        when(employeeRepository.findByAppUserId(5L)).thenReturn(Optional.of(employee));
        when(shiftRepository.findById(200L)).thenReturn(Optional.of(shift));
        when(absenceRequestRepository.findByShiftIdAndStatusIn(eq(200L), anyList()))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> absenceService.reportAbsence(200L, "sick", employeeUser));
    }

    // -----------------------------------------------------------------------
    // reportAbsence — rejects shift not owned by the user
    // -----------------------------------------------------------------------

    @Test
    void reportAbsenceRejectsShiftNotOwnedByUser() {
        AppUser employeeUser = mock(AppUser.class);
        when(employeeUser.getId()).thenReturn(5L);

        Employee myEmployee = new Employee();
        myEmployee.setId(99L);
        myEmployee.setStore(new Store("S", "A", 1.0));

        Employee otherEmployee = new Employee();
        otherEmployee.setId(88L); // different employee

        Shift shift = new Shift();
        shift.setId(200L);
        shift.setEmployee(otherEmployee); // belongs to someone else
        shift.setShiftDate(LocalDate.now().plusDays(5));
        shift.setShiftType("SHIFT_1_10_18");
        shift.setStatus("SCHEDULED");

        when(employeeRepository.findByAppUserId(5L)).thenReturn(Optional.of(myEmployee));
        when(shiftRepository.findById(200L)).thenReturn(Optional.of(shift));

        assertThrows(IllegalArgumentException.class,
                () -> absenceService.reportAbsence(200L, "sick", employeeUser));
    }

    // -----------------------------------------------------------------------
    // reportAbsence — allows REPLACEMENT shift to be reported
    // -----------------------------------------------------------------------

    @Test
    void reportAbsenceAllowsReplacementShift() {
        Store store = new Store("Central", "Main", 900.0);
        store.setId(10L);

        AppUser employeeUser = mock(AppUser.class);
        when(employeeUser.getId()).thenReturn(5L);

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setFullName("Ana Popescu");
        employee.setStore(store);

        Shift shift = new Shift();
        shift.setId(200L);
        shift.setEmployee(employee);
        shift.setShiftDate(LocalDate.now().plusDays(3));
        shift.setShiftType("SHIFT_1_10_18");
        shift.setStatus("REPLACEMENT"); // replacement shift is also reportable

        AppUser manager = mock(AppUser.class);

        when(employeeRepository.findByAppUserId(5L)).thenReturn(Optional.of(employee));
        when(shiftRepository.findById(200L)).thenReturn(Optional.of(shift));
        when(absenceRequestRepository.findByShiftIdAndStatusIn(eq(200L), anyList()))
                .thenReturn(Optional.empty());
        when(absenceRequestRepository.save(any(AbsenceRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByStoreIdAndRole(10L, Role.MANAGER))
                .thenReturn(List.of(manager));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> absenceService.reportAbsence(200L, "emergency", employeeUser));
        verify(absenceRequestRepository).save(any(AbsenceRequest.class));
    }

    // -----------------------------------------------------------------------
    // acknowledgeAbsence — rejects non-pending request
    // -----------------------------------------------------------------------

    @Test
    void acknowledgeAbsenceRejectsAlreadyProcessed() {
        AbsenceRequest absenceRequest = mock(AbsenceRequest.class);
        when(absenceRequest.getStatus()).thenReturn("COVERED");
        when(absenceRequestRepository.findById(1L)).thenReturn(Optional.of(absenceRequest));

        AppUser manager = mock(AppUser.class);

        assertThrows(IllegalArgumentException.class,
                () -> absenceService.acknowledgeAbsence(1L, manager));
    }

    // -----------------------------------------------------------------------
    // acknowledgeAbsence — rejects manager from different store
    // -----------------------------------------------------------------------

    @Test
    void acknowledgeAbsenceRejectsManagerFromDifferentStore() {
        Store storeA = new Store("A", "Addr A", 1.0);
        storeA.setId(1L);
        Store storeB = new Store("B", "Addr B", 1.0);
        storeB.setId(2L);

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setStore(storeA);

        AbsenceRequest absenceRequest = mock(AbsenceRequest.class);
        when(absenceRequest.getStatus()).thenReturn("PENDING");
        when(absenceRequest.getRequestingEmployee()).thenReturn(employee);

        when(absenceRequestRepository.findById(1L)).thenReturn(Optional.of(absenceRequest));

        AppUser manager = mock(AppUser.class);
        when(manager.getStore()).thenReturn(storeB); // different store

        assertThrows(IllegalArgumentException.class,
                () -> absenceService.acknowledgeAbsence(1L, manager));
    }
}
