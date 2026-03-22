package com.licenta.licentabackend.dto;

import java.time.LocalDate;

public class DayForecastDto {
    private final LocalDate date;
    private final Integer salesForecast;
    private final Boolean receptionDay;

    public DayForecastDto(LocalDate date, Integer salesForecast, Boolean receptionDay) {
        this.date = date;
        this.salesForecast = salesForecast;
        this.receptionDay = receptionDay;
    }

    public LocalDate getDate() {
        return date;
    }

    public Integer getSalesForecast() {
        return salesForecast;
    }

    public Boolean getReceptionDay() {
        return receptionDay;
    }

    @Override
    public String toString(){
        return "DayForecast [Date=" + date + ", Sales=" + salesForecast + ", Reception=" + receptionDay + "]";
    }


}
