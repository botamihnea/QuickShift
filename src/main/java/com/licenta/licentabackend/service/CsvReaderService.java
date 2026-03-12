package com.licenta.licentabackend.service;

import com.licenta.licentabackend.dto.DayForecastDto;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvReaderService {
    public List<DayForecastDto> readDataFromCsv(String filePath) {
        List<DayForecastDto> plannedDays = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isFirstLine = true;

            while ((line = br.readLine()) != null) {
                // Skip the header (first row)
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                // Split the line by comma (standard for CSV)
                String[] columns = line.split(",");

                if (columns.length >= 4) {
                    // Column A (index 0) = Date
                    LocalDate date = LocalDate.parse(columns[0].trim(), formatter);

                    // Column C (index 2) = Sales Forecast (Skipping Column B - Day of week)
                    Integer salesForecast = Integer.parseInt(columns[2].trim());

                    // Column D (index 3) = Merchandise Reception
                    String receptionText = columns[3].trim().toLowerCase();
                    Boolean receptionDay = receptionText.equals("da") || receptionText.equals("yes");

                    // Create the DTO object and add it to the list
                    DayForecastDto dayDto = new DayForecastDto(date, salesForecast, receptionDay);
                    plannedDays.add(dayDto);
                }
            }
            System.out.println("✅ Successfully read " + plannedDays.size() + " days from the CSV file!");

        } catch (Exception e) {
            System.err.println("❌ Error reading CSV file: " + e.getMessage());
        }

        return plannedDays;
    }
}