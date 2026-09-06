package com.example.weather.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public class WeatherRequest {

    @NotBlank
    private String pincode;

    //@Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    private LocalDate forDate;

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public LocalDate getForDate() {
        return forDate;
    }

    public void setForDate(LocalDate forDate) {
        this.forDate = forDate;
    }
}
