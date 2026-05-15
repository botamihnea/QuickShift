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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LeaveRequestService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_DENIED = "DENIED";

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final ShiftRepository shiftRepository;

    public LeaveRequestService(
            LeaveRequestRepository leaveRequestRepository,
            EmployeeRepository employeeRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            ShiftRepository shiftRepository
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.shiftRepository = shiftRepository;
    }

    @Transactional
    public LeaveRequestResponse requestLeave(AppUser currentUser, LeaveRequestCreateRequest request) {
        if (currentUser.getRole() != Role.EMPLOYEE) {
            throw new IllegalArgumentException("Only employees can request leave.");
        }

        if (request == null || request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required.");
        }

        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }

        if (startDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("You can only request leave for future dates.");
        }

        LocalDate allowedStart = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        LocalDate allowedEnd = allowedStart.plusMonths(1).minusDays(1);
        if (startDate.isBefore(allowedStart) || endDate.isAfter(allowedEnd)) {
            throw new IllegalArgumentException("Leave requests are only allowed for the next month.");
        }

        Employee employee = employeeRepository.findByAppUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee profile not found."));

        int remainingLeave = employee.getRemainingLeaveDays() != null ? employee.getRemainingLeaveDays() : 0;
        if (remainingLeave <= 0) {
            throw new IllegalArgumentException("No remaining leave days. Please request directly from your manager.");
        }

        int requestedDays = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
        int deductibleDays = calculateDeductibleDays(requestedDays);
        if (deductibleDays > remainingLeave) {
            throw new IllegalArgumentException("Requested days exceed your remaining leave balance.");
        }

        boolean overlaps = leaveRequestRepository.existsByRequestingEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            employee.getId(),
            List.of(STATUS_PENDING, STATUS_APPROVED),
            startDate,
            endDate
        );
        if (overlaps) {
            throw new IllegalArgumentException("You already have a leave request in this period.");
        }

        LeaveRequest leaveRequest = new LeaveRequest(
            employee,
            startDate,
            endDate,
            requestedDays,
            deductibleDays,
            request.reason()
        );
        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);

        Store store = employee.getStore();
        if (store != null) {
            List<AppUser> managers = userRepository.findByStoreIdAndRole(store.getId(), Role.MANAGER);
            String message = String.format(
                    "[LEAVE REQUEST] %s requested leave from %s to %s (%d days, %d deducted).",
                    employee.getFullName(),
                    startDate,
                    endDate,
                    requestedDays,
                    deductibleDays
            );
            for (AppUser manager : managers) {
                notificationRepository.save(new Notification(message, manager, store, null, saved.getId()));
            }
        }

        return new LeaveRequestResponse(saved.getId(), saved.getStatus(), requestedDays, deductibleDays, startDate, endDate);
    }

    @Transactional
    public LeaveRequestResponse approveLeave(Long requestId, AppUser manager) {
        if (manager.getRole() != Role.MANAGER) {
            throw new IllegalArgumentException("Only managers can approve leave requests.");
        }

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found."));

        if (!STATUS_PENDING.equals(leaveRequest.getStatus())) {
            throw new IllegalArgumentException("This leave request has already been processed.");
        }

        Employee employee = leaveRequest.getRequestingEmployee();
        Store store = employee.getStore();
        if (store == null || manager.getStore() == null || !store.getId().equals(manager.getStore().getId())) {
            throw new IllegalArgumentException("You are not the manager of this store.");
        }

        int remainingLeave = employee.getRemainingLeaveDays() != null ? employee.getRemainingLeaveDays() : 0;
        int deductibleDays = leaveRequest.getDeductibleDays() != null ? leaveRequest.getDeductibleDays() : leaveRequest.getRequestedDays();
        int requestedDays = leaveRequest.getRequestedDays();
        if (remainingLeave < deductibleDays) {
            throw new IllegalArgumentException("Employee does not have enough leave days remaining.");
        }

        employee.setRemainingLeaveDays(remainingLeave - deductibleDays);
        employeeRepository.save(employee);

        leaveRequest.setStatus(STATUS_APPROVED);
        leaveRequest.setDecidedAt(LocalDateTime.now());
        leaveRequestRepository.save(leaveRequest);

        markExistingShiftsAsAbsent(employee, leaveRequest.getStartDate(), leaveRequest.getEndDate());

        String managerMessage = String.format(
            "[LEAVE APPROVED] %s leave approved for %s to %s.",
            employee.getFullName(),
            leaveRequest.getStartDate(),
            leaveRequest.getEndDate()
        );
        notificationRepository.save(new Notification(managerMessage, manager, store));

        if (employee.getAppUser() != null) {
            String employeeMessage = String.format(
                    "[LEAVE APPROVED] Your leave from %s to %s has been approved.",
                    leaveRequest.getStartDate(),
                    leaveRequest.getEndDate()
            );
            notificationRepository.save(new Notification(employeeMessage, employee.getAppUser(), store));
        }

        return new LeaveRequestResponse(leaveRequest.getId(), leaveRequest.getStatus(), requestedDays, deductibleDays,
                leaveRequest.getStartDate(), leaveRequest.getEndDate());
    }

    @Transactional
    public LeaveRequestResponse denyLeave(Long requestId, AppUser manager, LeaveRequestDecisionRequest decision) {
        if (manager.getRole() != Role.MANAGER) {
            throw new IllegalArgumentException("Only managers can deny leave requests.");
        }

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found."));

        if (!STATUS_PENDING.equals(leaveRequest.getStatus())) {
            throw new IllegalArgumentException("This leave request has already been processed.");
        }

        Employee employee = leaveRequest.getRequestingEmployee();
        Store store = employee.getStore();
        if (store == null || manager.getStore() == null || !store.getId().equals(manager.getStore().getId())) {
            throw new IllegalArgumentException("You are not the manager of this store.");
        }

        String reason = decision != null ? decision.reason() : null;
        if (reason != null && reason.isBlank()) {
            reason = null;
        }

        leaveRequest.setStatus(STATUS_DENIED);
        leaveRequest.setManagerResponse(reason);
        leaveRequest.setDecidedAt(LocalDateTime.now());
        leaveRequestRepository.save(leaveRequest);

        if (employee.getAppUser() != null) {
            String employeeMessage = reason == null
                    ? String.format("[LEAVE DENIED] Your leave request from %s to %s was denied.",
                            leaveRequest.getStartDate(), leaveRequest.getEndDate())
                    : String.format("[LEAVE DENIED] Your leave request from %s to %s was denied. Reason: %s",
                            leaveRequest.getStartDate(), leaveRequest.getEndDate(), reason);
            notificationRepository.save(new Notification(employeeMessage, employee.getAppUser(), store));
        }

        return new LeaveRequestResponse(leaveRequest.getId(), leaveRequest.getStatus(), leaveRequest.getRequestedDays(),
                leaveRequest.getDeductibleDays(), leaveRequest.getStartDate(), leaveRequest.getEndDate());
    }

    private int calculateDeductibleDays(int requestedDays) {
        double coefficient = 5.0 / 7.0;
        return (int) Math.ceil(requestedDays * coefficient);
    }

    private void markExistingShiftsAsAbsent(Employee employee, LocalDate startDate, LocalDate endDate) {
        List<Shift> shifts = shiftRepository.findByEmployeeIdAndShiftDateBetween(employee.getId(), startDate, endDate);
        if (shifts.isEmpty()) {
            return;
        }

        for (Shift shift : shifts) {
            shift.setStatus("ABSENT");
        }
        shiftRepository.saveAll(shifts);
    }
}
