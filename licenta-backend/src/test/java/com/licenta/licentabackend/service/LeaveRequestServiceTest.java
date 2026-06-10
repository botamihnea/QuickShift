package com.licenta.licentabackend.service;

import com.licenta.licentabackend.domain.AppUser;
import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.LeaveRequest;
import com.licenta.licentabackend.domain.Notification;
import com.licenta.licentabackend.domain.Role;
import com.licenta.licentabackend.domain.Shift;
import com.licenta.licentabackend.domain.Store;
import com.licenta.licentabackend.dto.LeaveRequestCreateRequest;
import com.licenta.licentabackend.dto.LeaveRequestDecisionRequest;
import com.licenta.licentabackend.dto.LeaveRequestResponse;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.LeaveRequestRepository;
import com.licenta.licentabackend.repository.NotificationRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import com.licenta.licentabackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    @Test
    void requestLeaveRejectsNonEmployeeRole() {
        AppUser manager = org.mockito.Mockito.mock(AppUser.class);
        when(manager.getRole()).thenReturn(Role.MANAGER);

        LocalDate allowedStart = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                allowedStart.plusDays(1),
                allowedStart.plusDays(2),
                "personal"
        );

        assertThrows(IllegalArgumentException.class,
                () -> leaveRequestService.requestLeave(manager, request));
    }

    @Test
    void requestLeaveCreatesLeaveAndNotifiesManager() {
        Store store = new Store("Central", "Main", 900.0);
        store.setId(10L);

        AppUser currentUser = org.mockito.Mockito.mock(AppUser.class);
        when(currentUser.getRole()).thenReturn(Role.EMPLOYEE);
        when(currentUser.getId()).thenReturn(5L);

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setFullName("Ana Popescu");
        employee.setStore(store);
        employee.setRemainingLeaveDays(10);

        AppUser manager = org.mockito.Mockito.mock(AppUser.class);

        LocalDate allowedStart = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        LocalDate startDate = allowedStart.plusDays(1);
        LocalDate endDate = startDate.plusDays(2);
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(startDate, endDate, "vacation");

        when(employeeRepository.findByAppUserId(5L)).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.existsByRequestingEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(99L),
                any(),
                eq(startDate),
                eq(endDate)
        )).thenReturn(false);
        when(leaveRequestRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByStoreIdAndRole(10L, Role.MANAGER))
                .thenReturn(List.of(manager));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestResponse response = leaveRequestService.requestLeave(currentUser, request);

        assertEquals("PENDING", response.status());
        assertEquals(3, response.requestedDays());
        assertEquals(3, response.deductibleDays());
        assertEquals(startDate, response.startDate());
        assertEquals(endDate, response.endDate());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        String message = notificationCaptor.getValue().getMessage();
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("Ana Popescu"));
    }

    @Test
    void requestLeaveRejectsOverlappingRequests() {
        AppUser currentUser = org.mockito.Mockito.mock(AppUser.class);
        when(currentUser.getRole()).thenReturn(Role.EMPLOYEE);
        when(currentUser.getId()).thenReturn(7L);

        Employee employee = new Employee();
        employee.setId(55L);
        employee.setRemainingLeaveDays(5);

        LocalDate allowedStart = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                allowedStart.plusDays(2),
                allowedStart.plusDays(3),
                "trip"
        );

        when(employeeRepository.findByAppUserId(7L)).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.existsByRequestingEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(55L),
                any(),
                eq(request.startDate()),
                eq(request.endDate())
        )).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> leaveRequestService.requestLeave(currentUser, request));
    }

    @Test
    void requestLeaveRejectsPastStartDate() {
        AppUser currentUser = org.mockito.Mockito.mock(AppUser.class);
        when(currentUser.getRole()).thenReturn(Role.EMPLOYEE);

        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                "personal"
        );

        assertThrows(IllegalArgumentException.class,
                () -> leaveRequestService.requestLeave(currentUser, request));
    }

    @Test
    void requestLeaveRejectsWhenNoRemainingLeaveDays() {
        AppUser currentUser = org.mockito.Mockito.mock(AppUser.class);
        when(currentUser.getRole()).thenReturn(Role.EMPLOYEE);
        when(currentUser.getId()).thenReturn(8L);

        Employee employee = new Employee();
        employee.setId(66L);
        employee.setRemainingLeaveDays(0);

        LocalDate allowedStart = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                allowedStart.plusDays(2),
                allowedStart.plusDays(3),
                "trip"
        );

        when(employeeRepository.findByAppUserId(8L)).thenReturn(Optional.of(employee));

        assertThrows(IllegalArgumentException.class,
                () -> leaveRequestService.requestLeave(currentUser, request));
    }

    @Test
    void requestLeaveRejectsWhenRequestedDaysExceedBalance() {
        AppUser currentUser = org.mockito.Mockito.mock(AppUser.class);
        when(currentUser.getRole()).thenReturn(Role.EMPLOYEE);
        when(currentUser.getId()).thenReturn(9L);

        Employee employee = new Employee();
        employee.setId(77L);
        employee.setRemainingLeaveDays(1);

        LocalDate allowedStart = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                allowedStart.plusDays(1),
                allowedStart.plusDays(6),
                "family"
        );

        when(employeeRepository.findByAppUserId(9L)).thenReturn(Optional.of(employee));

        assertThrows(IllegalArgumentException.class,
                () -> leaveRequestService.requestLeave(currentUser, request));
    }

    @Test
    void approveLeaveUpdatesEmployeeBalanceAndMarksShiftsAbsent() {
        Store store = new Store("Central", "Main", 900.0);
        store.setId(3L);

        Employee employee = new Employee();
        employee.setId(11L);
        employee.setFullName("Maria Ionescu");
        employee.setRemainingLeaveDays(5);
        employee.setStore(store);
        employee.setAppUser(org.mockito.Mockito.mock(AppUser.class));

        LocalDate startDate = LocalDate.now().withDayOfMonth(1).plusMonths(1).plusDays(3);
        LocalDate endDate = startDate.plusDays(1);
        LeaveRequest leaveRequest = new LeaveRequest(employee, startDate, endDate, 2, 2, "family");

        AppUser manager = org.mockito.Mockito.mock(AppUser.class);
        when(manager.getRole()).thenReturn(Role.MANAGER);
        when(manager.getStore()).thenReturn(store);

        Shift shift = new Shift();
        shift.setEmployee(employee);
        shift.setShiftDate(startDate);
        shift.setShiftType("SHIFT_1_10_18");

        when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leaveRequest));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leaveRequestRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftRepository.findByEmployeeIdAndShiftDateBetween(employee.getId(), startDate, endDate))
                .thenReturn(List.of(shift));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestResponse response = leaveRequestService.approveLeave(100L, manager);

        assertEquals("APPROVED", response.status());
        assertEquals(3, employee.getRemainingLeaveDays());
        assertEquals("ABSENT", shift.getStatus());
        verify(shiftRepository).saveAll(List.of(shift));
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

        @Test
        void approveLeaveRejectsNonManagerRole() {
                AppUser employeeUser = org.mockito.Mockito.mock(AppUser.class);
                when(employeeUser.getRole()).thenReturn(Role.EMPLOYEE);

                assertThrows(IllegalArgumentException.class,
                                () -> leaveRequestService.approveLeave(10L, employeeUser));
        }

        @Test
        void approveLeaveRejectsDifferentStoreManager() {
                Store store = new Store("Central", "Main", 900.0);
                store.setId(1L);

                Store otherStore = new Store("Other", "Side", 800.0);
                otherStore.setId(2L);

                Employee employee = new Employee();
                employee.setId(12L);
                employee.setStore(store);

                LocalDate startDate = LocalDate.now().withDayOfMonth(1).plusMonths(1).plusDays(2);
                LocalDate endDate = startDate.plusDays(1);
                LeaveRequest leaveRequest = new LeaveRequest(employee, startDate, endDate, 2, 2, "trip");

                AppUser manager = org.mockito.Mockito.mock(AppUser.class);
                when(manager.getRole()).thenReturn(Role.MANAGER);
                when(manager.getStore()).thenReturn(otherStore);

                when(leaveRequestRepository.findById(300L)).thenReturn(Optional.of(leaveRequest));

                assertThrows(IllegalArgumentException.class,
                                () -> leaveRequestService.approveLeave(300L, manager));
        }

    @Test
    void denyLeaveStoresBlankReasonAsNull() {
        Store store = new Store("Central", "Main", 900.0);
        store.setId(7L);

        Employee employee = new Employee();
        employee.setId(21L);
        employee.setStore(store);
        employee.setAppUser(org.mockito.Mockito.mock(AppUser.class));

        LocalDate startDate = LocalDate.now().withDayOfMonth(1).plusMonths(1).plusDays(5);
        LocalDate endDate = startDate.plusDays(1);
        LeaveRequest leaveRequest = new LeaveRequest(employee, startDate, endDate, 2, 2, "trip");

        AppUser manager = org.mockito.Mockito.mock(AppUser.class);
        when(manager.getRole()).thenReturn(Role.MANAGER);
        when(manager.getStore()).thenReturn(store);

        when(leaveRequestRepository.findById(200L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestDecisionRequest decision = new LeaveRequestDecisionRequest("  ");
        LeaveRequestResponse response = leaveRequestService.denyLeave(200L, manager, decision);

        assertEquals("DENIED", response.status());
        assertNull(leaveRequest.getManagerResponse());
    }

        @Test
        void denyLeaveKeepsNonBlankReason() {
                Store store = new Store("Central", "Main", 900.0);
                store.setId(8L);

                Employee employee = new Employee();
                employee.setId(31L);
                employee.setStore(store);
                employee.setAppUser(org.mockito.Mockito.mock(AppUser.class));

                LocalDate startDate = LocalDate.now().withDayOfMonth(1).plusMonths(1).plusDays(6);
                LocalDate endDate = startDate.plusDays(1);
                LeaveRequest leaveRequest = new LeaveRequest(employee, startDate, endDate, 2, 2, "travel");

                AppUser manager = org.mockito.Mockito.mock(AppUser.class);
                when(manager.getRole()).thenReturn(Role.MANAGER);
                when(manager.getStore()).thenReturn(store);

                when(leaveRequestRepository.findById(400L)).thenReturn(Optional.of(leaveRequest));
                when(leaveRequestRepository.save(any(LeaveRequest.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(notificationRepository.save(any(Notification.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                LeaveRequestDecisionRequest decision = new LeaveRequestDecisionRequest("Not enough coverage");
                LeaveRequestResponse response = leaveRequestService.denyLeave(400L, manager, decision);

                assertEquals("DENIED", response.status());
                assertEquals("Not enough coverage", leaveRequest.getManagerResponse());
        }
}
