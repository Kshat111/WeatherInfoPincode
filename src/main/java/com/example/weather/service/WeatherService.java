package com.example.weather.service;

import com.example.weather.dto.WeatherResponse;

import java.time.LocalDate;

public interface WeatherService {
    WeatherResponse getWeather(String pincode, LocalDate forDate);
}
