package com.example.weather.service;

import com.example.weather.adapters.GeocodeResult;
import com.example.weather.adapters.OpenWeatherClient;
import com.example.weather.dto.WeatherResponse;
import com.example.weather.model.Location;
import com.example.weather.model.WeatherRecord;
import com.example.weather.redis.RedisLockService;
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
    private RedisLockService lockService;
    private com.example.weather.redis.RedisCacheService cacheService;
    private WeatherServiceImpl service;

    @BeforeEach
    public void setup() {
        locationRepository = mock(LocationRepository.class);
        weatherRecordRepository = mock(WeatherRecordRepository.class);
        openWeatherClient = mock(OpenWeatherClient.class);
        objectMapper = new ObjectMapper();
        lockService = mock(RedisLockService.class);
        cacheService = mock(com.example.weather.redis.RedisCacheService.class);

        service = new WeatherServiceImpl(locationRepository, weatherRecordRepository, openWeatherClient, objectMapper, lockService, cacheService);
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

        // No weather record exists, lock success
        when(weatherRecordRepository.findByLocationAndDate(any(Location.class), any(LocalDate.class))).thenReturn(Optional.empty());
        when(cacheService.get(anyString())).thenReturn(null);
        when(lockService.tryLock(anyString(), anyLong())).thenReturn(true);
        when(openWeatherClient.getCurrentWeather(18.5,73.8)).thenReturn("{ \"weather\": [{\"description\": \"clear\"}], \"main\": {\"temp\": 25} }");
        when(weatherRecordRepository.save(any(WeatherRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        WeatherResponse resp = service.getWeather("411014", LocalDate.of(2020,10,15));
        assertThat(resp).isNotNull();
        assertThat(resp.getPincode()).isEqualTo("411014");
        verify(openWeatherClient).geocodeByPincode("411014");
        verify(openWeatherClient).getCurrentWeather(18.5,73.8);
    }

    @Test
    public void when_lock_not_acquired_poll_for_record() {
        Location loc = new Location();
        loc.setId(1L);
        loc.setPincode("411014");
        loc.setLatitude(18.5);
        loc.setLongitude(73.8);
        when(locationRepository.findByPincode("411014")).thenReturn(Optional.of(loc));
        when(weatherRecordRepository.findByLocationAndDate(loc, LocalDate.of(2020,10,15))).thenReturn(Optional.empty());
        when(cacheService.get(anyString())).thenReturn(null);
        when(lockService.tryLock(anyString(), anyLong())).thenReturn(false);
        when(weatherRecordRepository.findByLocationAndDate(any(Location.class), any(LocalDate.class))).thenReturn(Optional.empty());

        try {
            service.getWeather("411014", LocalDate.of(2020,10,15));
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Failed to acquire lock");
        }
    }
}
