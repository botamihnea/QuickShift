package com.licenta.licentabackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.licenta.licentabackend.service.CsvReaderService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LicentaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LicentaBackendApplication.class, args);
    }

    @Bean
    CommandLineRunner testCsvReader(CsvReaderService csvReaderService) {
        return args -> {
            String filePath = "E:\\An3\\Licenta\\PrognozaDeVenit.csv";
            var forecastDays = csvReaderService.readDataFromCsv(filePath);

            for (var day : forecastDays) {
                System.out.println(day);
            }
        };
    }

}
