package com.licenta.licentabackend;

import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class LicentaBackendApplication {

    private static final Logger log = LoggerFactory.getLogger(LicentaBackendApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LicentaBackendApplication.class, args);
    }

    @Bean
    CommandLineRunner runApplicationLogic(EmployeeRepository employeeRepository) {
        return args -> {
            log.info("Starting application logic...");

            if (employeeRepository.count() == 0) {
                log.info("No employees found in DB. Seeding initial test data...");

                Employee emp1= new Employee();
                emp1.setFullName("Mihai Dumitrescu");
                emp1.setRole("FLORIST");
                emp1.setContractType("FULL_TIME_8H");
                emp1.setShiftPreference("MORNING"); // Will prioritize SHIFT_1

                Employee emp2 = new Employee();
                emp2.setFullName("Andreea Munteanu");
                emp2.setRole("FLORIST");
                emp2.setContractType("FULL_TIME_8H");
                emp2.setShiftPreference("EVENING"); // Will prioritize SHIFT_2

                Employee emp3 = new Employee();
                emp3.setFullName("Florin Neagu");
                emp3.setRole("FLORIST");
                emp3.setContractType("FULL_TIME_8H");
                emp3.setShiftPreference("ANY"); // Flexible, algorithm decides based on needs

                Employee emp4 = new Employee();
                emp4.setFullName("Diana Constantin");
                emp4.setRole("FLORIST");
                emp4.setContractType("PART_TIME_6H"); // Testing the 6-hour logic
                emp4.setShiftPreference("EVENING"); // This will force her into the evening slots!

                Employee emp5 = new Employee();
                emp5.setFullName("Bogdan Marin");
                emp5.setRole("FLORIST");
                emp5.setContractType("PART_TIME_4H");
                emp5.setShiftPreference("MORNING");

                List<Employee> employees = List.of(emp1, emp2, emp3, emp4, emp5);

                employeeRepository.saveAll(employees);
                log.info("5 mock employees successfully added to the database.");
            }

            log.info("Application ready. Use POST /api/shifts/generate to generate shifts for next month.");
        };
    }

}
