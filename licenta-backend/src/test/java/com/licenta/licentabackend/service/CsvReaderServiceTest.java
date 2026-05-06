package com.licenta.licentabackend.service;

import com.licenta.licentabackend.dto.DayForecastDto;
import com.licenta.licentabackend.exceptions.FailedReadingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvReaderServiceTest {

    private final CsvReaderService csvReaderService = new CsvReaderService();

    @Test
    void readDataFromCsvForMonthFiltersByMonth(@TempDir Path tempDir) throws Exception {
        Path csvPath = tempDir.resolve("forecast.csv");
        List<String> lines = List.of(
                "Date,Day,Sales,Reception",
                "01/05/2026,Mon,1200,yes",
                "02/05/2026,Tue,1500,da",
                "28/04/2026,Wed,900,no"
        );
        Files.write(csvPath, lines);

        List<DayForecastDto> result = csvReaderService.readDataFromCsvForMonth(
                csvPath.toString(),
                YearMonth.of(2026, 5)
        );

        assertEquals(2, result.size());
        assertEquals(YearMonth.of(2026, 5), YearMonth.from(result.get(0).getDate()));
    }

    @Test
    void readDataFromCsvThrowsOnInvalidPath() {
        assertThrows(FailedReadingException.class, () -> csvReaderService.readDataFromCsv("missing.csv"));
    }
}
