package com.licenta.licentabackend.repository;

import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Shift;
import com.licenta.licentabackend.domain.Store;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShiftRepositoryIntegrationTest {

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Test
    void findByEmployeeStoreIdAndShiftDateBetweenReturnsOnlyMatchingShifts() {
        Store store = storeRepository.save(new Store("Central", "Main", 900.0));
        Store otherStore = storeRepository.save(new Store("North", "Side", 800.0));

        Employee employee = new Employee();
        employee.setFullName("Ana Popescu");
        employee.setContractType("FULL_TIME_8H");
        employee.setStore(store);
        employee = employeeRepository.save(employee);

        Employee otherEmployee = new Employee();
        otherEmployee.setFullName("Ion Pop");
        otherEmployee.setContractType("PART_TIME_4H");
        otherEmployee.setStore(otherStore);
        otherEmployee = employeeRepository.save(otherEmployee);

        Shift shift1 = new Shift();
        shift1.setEmployee(employee);
        shift1.setShiftDate(LocalDate.of(2026, 9, 10));
        shift1.setShiftType("SHIFT_1_10_18");
        shiftRepository.save(shift1);

        Shift shift2 = new Shift();
        shift2.setEmployee(employee);
        shift2.setShiftDate(LocalDate.of(2026, 9, 20));
        shift2.setShiftType("SHIFT_1_10_18");
        shiftRepository.save(shift2);

        Shift otherShift = new Shift();
        otherShift.setEmployee(otherEmployee);
        otherShift.setShiftDate(LocalDate.of(2026, 9, 12));
        otherShift.setShiftType("SHIFT_1_10_18");
        shiftRepository.save(otherShift);

        List<Shift> results = shiftRepository.findByEmployeeStoreIdAndShiftDateBetween(
                store.getId(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 15)
        );

        assertEquals(1, results.size());
        assertEquals(employee.getId(), results.get(0).getEmployee().getId());
        assertEquals(LocalDate.of(2026, 9, 10), results.get(0).getShiftDate());
    }
}
