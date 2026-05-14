package com.licenta.licentabackend.service;

import com.licenta.licentabackend.dto.DayForecastDto;
import com.licenta.licentabackend.exceptions.FailedReadingException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvReaderService {

    private static final Logger log = LoggerFactory.getLogger(CsvReaderService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<DayForecastDto> readDataFromCsv(String filePath) {
        List<DayForecastDto> plannedDays = new ArrayList<>();
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
                    LocalDate date = LocalDate.parse(columns[0].trim(), DATE_FORMATTER);

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
            log.info("✅ Successfully read {} days from the CSV file!", plannedDays.size());

        } catch (Exception e) {
            throw new FailedReadingException("❌ Error reading CSV file: " + e.getMessage());
        }

        return plannedDays;
    }

    public List<DayForecastDto> readDataFromCsvForMonth(String filePath, YearMonth targetMonth) {
        return readDataFromCsvForMonth(filePath, targetMonth, null);
    }

    public List<DayForecastDto> readDataFromCsvForMonth(String filePath, YearMonth targetMonth, String sheetName) {
        List<DayForecastDto> allDays = readForecastData(filePath, sheetName);

        return allDays.stream()
                .filter(day -> YearMonth.from(day.getDate()).equals(targetMonth))
                .toList();
    }

    private List<DayForecastDto> readForecastData(String filePath, String sheetName) {
        if (isExcelFile(filePath)) {
            return readDataFromExcel(filePath, sheetName);
        }
        return readDataFromCsv(filePath);
    }

    private boolean isExcelFile(String filePath) {
        String lower = filePath == null ? "" : filePath.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private List<DayForecastDto> readDataFromExcel(String filePath, String sheetName) {
        List<DayForecastDto> plannedDays = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream input = Files.newInputStream(Path.of(filePath));
             Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = resolveSheet(workbook, sheetName);
            if (sheet == null) {
                throw new FailedReadingException("❌ Sheet not found: " + sheetName);
            }

            boolean isFirstRow = true;
            for (Row row : sheet) {
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }

                LocalDate date = readDateCell(row.getCell(0), formatter);
                Integer salesForecast = readIntegerCell(row.getCell(2), formatter);
                Boolean receptionDay = readBooleanCell(row.getCell(3), formatter);

                if (date == null || salesForecast == null || receptionDay == null) {
                    continue;
                }

                plannedDays.add(new DayForecastDto(date, salesForecast, receptionDay));
            }

            log.info("✅ Successfully read {} days from the Excel sheet!", plannedDays.size());
        } catch (Exception e) {
            throw new FailedReadingException("❌ Error reading Excel file: " + e.getMessage());
        }

        return plannedDays;
    }

    private Sheet resolveSheet(Workbook workbook, String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
        }
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet != null) {
            return sheet;
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
            Sheet candidate = workbook.getSheetAt(i);
            if (candidate.getSheetName().trim().equalsIgnoreCase(sheetName.trim())) {
                return candidate;
            }
        }
        return null;
    }

    private LocalDate readDateCell(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }

        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }

        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        String text = formatter.formatCellValue(cell).trim();
        if (text.isEmpty()) {
            return null;
        }
        return LocalDate.parse(text, DATE_FORMATTER);
    }

    private Integer readIntegerCell(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }

        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }

        if (type == CellType.NUMERIC) {
            return (int) Math.round(cell.getNumericCellValue());
        }

        String text = formatter.formatCellValue(cell).trim();
        if (text.isEmpty()) {
            return null;
        }

        String numeric = text.replaceAll("[^0-9-]", "");
        if (numeric.isEmpty()) {
            return null;
        }
        return Integer.parseInt(numeric);
    }

    private Boolean readBooleanCell(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }

        String text = formatter.formatCellValue(cell).trim().toLowerCase();
        if (text.isEmpty()) {
            return null;
        }

        return text.equals("da") || text.equals("yes") || text.equals("true") || text.equals("1");
    }
}