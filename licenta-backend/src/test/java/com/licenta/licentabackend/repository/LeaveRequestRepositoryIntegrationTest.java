package com.licenta.licentabackend.repository;

import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.LeaveRequest;
import com.licenta.licentabackend.domain.Store;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LeaveRequestRepositoryIntegrationTest {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Test
    void existsByRequestingEmployeeIdAndStatusInDetectsOverlap() {
        Store store = storeRepository.save(new Store("Central", "Main", 900.0));

        Employee employee = new Employee();
        employee.setFullName("Ana Popescu");
        employee.setContractType("FULL_TIME_8H");
        employee.setStore(store);
        employee = employeeRepository.save(employee);

        LocalDate start = LocalDate.of(2026, 7, 10);
        LocalDate end = LocalDate.of(2026, 7, 12);
        leaveRequestRepository.saveAndFlush(new LeaveRequest(employee, start, end, 3, 3, "trip"));

        boolean overlaps = leaveRequestRepository
                .existsByRequestingEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee.getId(),
                        List.of("PENDING", "APPROVED"),
                        LocalDate.of(2026, 7, 11),
                        LocalDate.of(2026, 7, 8)
                );

        boolean noOverlap = leaveRequestRepository
                .existsByRequestingEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee.getId(),
                        List.of("PENDING", "APPROVED"),
                        LocalDate.of(2026, 7, 21),
                        LocalDate.of(2026, 7, 20)
                );

        assertTrue(overlaps);
        assertFalse(noOverlap);
    }

    @Test
    void findByStatusAndStoreAndDateRangeFiltersCorrectly() {
        Store storeA = storeRepository.save(new Store("Central", "Main", 900.0));
        Store storeB = storeRepository.save(new Store("North", "Side", 800.0));

        Employee employeeA = new Employee();
        employeeA.setFullName("Maria Ionescu");
        employeeA.setContractType("FULL_TIME_8H");
        employeeA.setStore(storeA);
        employeeA = employeeRepository.save(employeeA);

        Employee employeeB = new Employee();
        employeeB.setFullName("Ion Pop");
        employeeB.setContractType("PART_TIME_6H");
        employeeB.setStore(storeB);
        employeeB = employeeRepository.save(employeeB);

        LocalDate start = LocalDate.of(2026, 8, 5);
        LocalDate end = LocalDate.of(2026, 8, 7);
        leaveRequestRepository.saveAndFlush(new LeaveRequest(employeeA, start, end, 3, 3, "vacation"));
        leaveRequestRepository.saveAndFlush(new LeaveRequest(employeeB, start, end, 3, 3, "vacation"));

        List<LeaveRequest> storeARequests = leaveRequestRepository
                .findByStatusAndRequestingEmployeeStoreIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        "PENDING",
                        storeA.getId(),
                        LocalDate.of(2026, 8, 5),
                        LocalDate.of(2026, 8, 7)
                );

        assertEquals(1, storeARequests.size());
        assertEquals(employeeA.getId(), storeARequests.get(0).getRequestingEmployee().getId());
    }
}
