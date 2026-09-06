package com.example.weather.api;

import com.example.weather.dto.WeatherRequest;
import com.example.weather.dto.WeatherResponse;
import com.example.weather.service.WeatherService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @PostMapping
    public ResponseEntity<WeatherResponse> getWeather(@Valid @RequestBody WeatherRequest request) {
        WeatherResponse resp = weatherService.getWeather(request.getPincode(), request.getForDate());
        return ResponseEntity.ok(resp);
    }
}
