package com.example.weather.service;

import com.example.weather.adapters.GeocodeResult;
import com.example.weather.adapters.OpenWeatherClient;
import com.example.weather.dto.WeatherResponse;
import com.example.weather.model.Location;
import com.example.weather.model.WeatherRecord;
import com.example.weather.repository.LocationRepository;
import com.example.weather.repository.WeatherRecordRepository;
import com.example.weather.service.impl.WeatherServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class WeatherServiceImplTest {

    private LocationRepository locationRepository;
    private WeatherRecordRepository weatherRecordRepository;
    private OpenWeatherClient openWeatherClient;
    private ObjectMapper objectMapper;
    // no redis lock service in this assignment
    private WeatherServiceImpl service;

    @BeforeEach
    public void setup() {
        locationRepository = mock(LocationRepository.class);
        weatherRecordRepository = mock(WeatherRecordRepository.class);
        openWeatherClient = mock(OpenWeatherClient.class);
        objectMapper = new ObjectMapper();
        lockService = mock(RedisLockService.class);

        service = new com.example.weather.service.impl.WeatherServiceImpl(locationRepository, weatherRecordRepository, openWeatherClient, objectMapper);
    }

    @Test
    public void when_location_missing_geocode_and_save() {
        when(locationRepository.findByPincode("411014")).thenReturn(Optional.empty());
        when(openWeatherClient.geocodeByPincode("411014")).thenReturn(new GeocodeResult(18.5,73.8,"Pune"));
        Location savedLoc = new Location();
        savedLoc.setId(1L);
        savedLoc.setPincode("411014");
        savedLoc.setLatitude(18.5);
        savedLoc.setLongitude(73.8);
        when(locationRepository.save(any(Location.class))).thenReturn(savedLoc);
        // No weather record exists
        when(weatherRecordRepository.findByLocationAndDate(any(Location.class), any(LocalDate.class))).thenReturn(Optional.empty());
        when(openWeatherClient.getCurrentWeather(18.5,73.8)).thenReturn("{ \"weather\": [{\"description\": \"clear\"}], \"main\": {\"temp\": 25, \"humidity\": 50, \"pressure\": 1012}, \"wind\": {\"speed\": 1.2} }");
        when(weatherRecordRepository.saveAndFlush(any(WeatherRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        WeatherResponse resp = service.getWeather("411014", LocalDate.now());
        assertThat(resp).isNotNull();
        assertThat(resp.getPincode()).isEqualTo("411014");
        verify(openWeatherClient).geocodeByPincode("411014");
        verify(openWeatherClient).getCurrentWeather(18.5,73.8);
    }

    @Test
    public void when_save_conflict_on_weather_then_reread() {
        Location loc = new Location();
        loc.setId(1L);
        loc.setPincode("411014");
        loc.setLatitude(18.5);
        loc.setLongitude(73.8);
        when(locationRepository.findByPincode("411014")).thenReturn(Optional.of(loc));
        when(weatherRecordRepository.findByLocationAndDate(loc, LocalDate.now())).thenReturn(Optional.empty());
        when(openWeatherClient.getCurrentWeather(18.5,73.8)).thenReturn("{ \"weather\": [{\"description\": \"clear\"}], \"main\": {\"temp\": 25, \"humidity\": 50, \"pressure\": 1012}, \"wind\": {\"speed\": 1.2} }");

        WeatherRecord existing = new WeatherRecord();
        existing.setId(10L);
        existing.setLocation(loc);
        existing.setDate(LocalDate.now());
        existing.setTemperatureC(25.0);
        when(weatherRecordRepository.saveAndFlush(any(WeatherRecord.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        when(weatherRecordRepository.findByLocationAndDate(loc, LocalDate.now())).thenReturn(Optional.of(existing));

        WeatherResponse resp = service.getWeather("411014", LocalDate.now());
        assertThat(resp).isNotNull();
        assertThat(resp.getTemperature()).isEqualTo(25.0);
    }
}
