package com.licenta.licentabackend;

import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import com.licenta.licentabackend.service.CsvReaderService;
import com.licenta.licentabackend.service.SchedulingService;
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
    CommandLineRunner runApplicationLogic(CsvReaderService csvReaderService,
                                          SchedulingService schedulingService,
                                          EmployeeRepository employeeRepository,
                                          ShiftRepository shiftRepository) {
        return args -> {
            log.info("Starting application logic...");

            shiftRepository.deleteAll(); //momentan pentru a tine db ul liber

            if (employeeRepository.count() == 0) {
                log.info("No employees found in DB. Seeding initial test data...");

                Employee emp1 = new Employee();
                emp1.setFullName("Maria Popescu");
                emp1.setRole("MANAGER");
                emp1.setContractType("FULL_TIME_8H");

                Employee emp2 = new Employee();
                emp2.setFullName("Elena Ionescu");
                emp2.setRole("FLORIST");
                emp2.setContractType("FULL_TIME_8H");

                Employee emp3 = new Employee();
                emp3.setFullName("Ioana Radu");
                emp3.setRole("FLORIST");
                emp3.setContractType("FULL_TIME_8H");

                Employee emp4 = new Employee();
                emp4.setFullName("Andrei Vasile");
                emp4.setRole("FLORIST");
                emp4.setContractType("FULL_TIME_8H");

                Employee emp5 = new Employee();
                emp5.setFullName("Ana Stan");
                emp5.setRole("FLORIST");
                emp5.setContractType("PART_TIME_4H");

                List<Employee> employees = List.of(emp1, emp2, emp3, emp4, emp5);

                employeeRepository.saveAll(employees);
                log.info("5 mock employees successfully added to the database.");
            }
            String filePath = "E:\\An3\\Licenta\\PrognozaDeVenit.csv";
            var forecastDays = csvReaderService.readDataFromCsv(filePath);

            log.info("Generating schedule based on read forecast...");

            schedulingService.generateSchedule(forecastDays);

            log.info("Successfully generated schedule");
        };
    }

}
