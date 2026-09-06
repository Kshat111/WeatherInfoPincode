package com.example.weather.api;

import com.example.weather.dto.WeatherResponse;
import com.example.weather.service.WeatherService;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping
    public ResponseEntity<WeatherResponse> getWeather(
            @RequestParam("pincode") @Pattern(regexp = "\\d{4,10}") String pincode,
            @RequestParam("for_date") @DateTimeFormat(iso = ISO.DATE) LocalDate forDate) {

        WeatherResponse resp = weatherService.getWeather(pincode, forDate);
        return ResponseEntity.ok(resp);
    }
}
