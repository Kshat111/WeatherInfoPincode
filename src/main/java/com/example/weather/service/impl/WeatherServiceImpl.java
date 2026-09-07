package com.example.weather.service.impl;

import com.example.weather.adapters.GeocodeResult;
import com.example.weather.adapters.OpenWeatherClient;
import com.example.weather.dto.WeatherResponse;
import com.example.weather.exception.UpstreamServiceException;
import com.example.weather.model.Location;
import com.example.weather.model.WeatherRecord;
import com.example.weather.repository.LocationRepository;
import com.example.weather.repository.WeatherRecordRepository;
import com.example.weather.service.WeatherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
public class WeatherServiceImpl implements WeatherService {

    private final LocationRepository locationRepository;
    private final WeatherRecordRepository weatherRecordRepository;
    private final OpenWeatherClient openWeatherClient;
    private final ObjectMapper objectMapper;

    public WeatherServiceImpl(LocationRepository locationRepository,
                              WeatherRecordRepository weatherRecordRepository,
                              OpenWeatherClient openWeatherClient,
                              ObjectMapper objectMapper) {
        this.locationRepository = locationRepository;
        this.weatherRecordRepository = weatherRecordRepository;
        this.openWeatherClient = openWeatherClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public WeatherResponse getWeather(String pincode, LocalDate forDate) {
        // 1. Lookup or create Location
        Location loc = locationRepository.findByPincode(pincode).orElse(null);
        if (loc == null) {
            GeocodeResult geo = openWeatherClient.geocodeByPincode(pincode);
            Location toSave = new Location();
            toSave.setPincode(pincode);
            toSave.setLatitude(geo.getLatitude());
            toSave.setLongitude(geo.getLongitude());
            toSave.setPlaceName(geo.getName());
            toSave.setSource("openweather");
            toSave.setCreatedAt(OffsetDateTime.now());
            try {
                loc = locationRepository.saveAndFlush(toSave);
            } catch (DataIntegrityViolationException dive) {
                // concurrent insert, re-read
                loc = locationRepository.findByPincode(pincode).orElseThrow(() -> new UpstreamServiceException("Failed to persist or read location"));
            }
        } else {
            // ensure placeName is populated if missing
            if ((loc.getPlaceName() == null || loc.getPlaceName().isBlank())) {
                try {
                    GeocodeResult geo = openWeatherClient.geocodeByPincode(pincode);
                    loc.setPlaceName(geo.getName());
                    try {
                        locationRepository.saveAndFlush(loc);
                    } catch (DataIntegrityViolationException ignore) {
                        // ignore concurrent update
                    }
                } catch (Exception ignored) {
                    // non-fatal; proceed
                }
            }
        }

        // 2. Lookup or create WeatherRecord
        var existing = weatherRecordRepository.findByLocationAndDate(loc, forDate);
        WeatherRecord record = existing.orElse(null);
        if (record == null) {
            String raw;
            if (forDate.equals(LocalDate.now())) {
                raw = openWeatherClient.getCurrentWeather(loc.getLatitude(), loc.getLongitude());
            } else {
                long unixTs = forDate.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
                raw = openWeatherClient.getHistoricalWeather(loc.getLatitude(), loc.getLongitude(), unixTs);
            }

            try {
                JsonNode root = objectMapper.readTree(raw);
                double temp = root.path("main").path("temp").asDouble();
                int humidity = root.path("main").path("humidity").asInt();
                int pressure = root.path("main").path("pressure").asInt();
                double wind = root.path("wind").path("speed").asDouble();
                String desc = null;
                if (root.path("weather").isArray() && root.path("weather").size() > 0) {
                    desc = root.path("weather").get(0).path("description").asText(null);
                }

                WeatherRecord wr = new WeatherRecord();
                wr.setLocation(loc);
                wr.setDate(forDate);
                wr.setRawPayload(raw);
                wr.setTemperatureC(temp);
                wr.setHumidity(humidity);
                wr.setPressure(pressure);
                wr.setWindSpeed(wind);
                wr.setDescription(desc);
                wr.setFetchedAt(OffsetDateTime.now());
                try {
                    record = weatherRecordRepository.saveAndFlush(wr);
                } catch (DataIntegrityViolationException dive) {
                    // concurrent insert; re-read existing
                    record = weatherRecordRepository.findByLocationAndDate(loc, forDate).orElseThrow(() -> new UpstreamServiceException("Failed to persist or read weather record"));
                }
            } catch (UpstreamServiceException use) {
                throw use;
            } catch (Exception e) {
                throw new UpstreamServiceException("Failed to parse weather response", e);
            }
        }

        // 3. Build response using place name
        WeatherResponse resp = new WeatherResponse();
        resp.setPincode(pincode);
        resp.setPlace(loc.getPlaceName() == null ? loc.getPincode() : loc.getPlaceName());
        resp.setDate(forDate);
        resp.setTemperature(record.getTemperatureC());
        resp.setHumidity(record.getHumidity());
        resp.setPressure(record.getPressure());
        resp.setWindSpeed(record.getWindSpeed());
        resp.setDescription(record.getDescription());

        return resp;
    }
}
