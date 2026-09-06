package com.example.weather.service.impl;

import com.example.weather.adapters.GeocodeResult;
import com.example.weather.adapters.OpenWeatherClient;
import com.example.weather.dto.WeatherResponse;
import com.example.weather.model.Location;
import com.example.weather.model.WeatherRecord;
import com.example.weather.repository.LocationRepository;
import com.example.weather.repository.WeatherRecordRepository;
import com.example.weather.service.WeatherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
public class WeatherServiceImpl implements WeatherService {

    private final LocationRepository locationRepository;
    private final WeatherRecordRepository weatherRecordRepository;
    private final OpenWeatherClient openWeatherClient;
    private final ObjectMapper objectMapper;
    private final com.example.weather.redis.RedisLockService lockService;
    private final com.example.weather.redis.RedisCacheService cacheService;

    public WeatherServiceImpl(LocationRepository locationRepository,
                              WeatherRecordRepository weatherRecordRepository,
                              OpenWeatherClient openWeatherClient,
                              ObjectMapper objectMapper,
                              com.example.weather.redis.RedisLockService lockService,
                              com.example.weather.redis.RedisCacheService cacheService) {
        this.locationRepository = locationRepository;
        this.weatherRecordRepository = weatherRecordRepository;
        this.openWeatherClient = openWeatherClient;
        this.objectMapper = objectMapper;
        this.lockService = lockService;
        this.cacheService = cacheService;
    }

    @Override
    public WeatherResponse getWeather(String pincode, LocalDate forDate) {
        // Find or create location
        Location loc = locationRepository.findByPincode(pincode).orElseGet(() -> {
            GeocodeResult geo = openWeatherClient.geocodeByPincode(pincode);
            Location l = new Location();
            l.setPincode(pincode);
            l.setLatitude(geo.getLatitude());
            l.setLongitude(geo.getLongitude());
            l.setSource("openweather");
            l.setCreatedAt(OffsetDateTime.now());
            return locationRepository.save(l);
        });

        // Check Redis cache first
        String cacheKey = "cache:weather:" + pincode + ":" + forDate.toString();
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            try {
                JsonNode cachedNode = objectMapper.readTree(cached);
                WeatherResponse respCached = new WeatherResponse();
                respCached.setPincode(pincode);
                respCached.setForDate(forDate);
                respCached.setLatitude(cachedNode.path("latitude").asDouble(0.0));
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
                        // Find or create location with save-and-reread to avoid TOCTOU race
                        Location loc = locationRepository.findByPincode(pincode).orElse(null);
                        if (loc == null) {
                            GeocodeResult geo = openWeatherClient.geocodeByPincode(pincode);
                            Location l = new Location();
                            l.setPincode(pincode);
                            l.setLatitude(geo.getLatitude());
                            l.setLongitude(geo.getLongitude());
                            l.setSource("openweather");
                            l.setCreatedAt(OffsetDateTime.now());
                            try {
                                loc = locationRepository.saveAndFlush(l);
                            } catch (DataIntegrityViolationException dive) {
                                // Another request inserted it concurrently — re-read
                                loc = locationRepository.findByPincode(pincode).orElseThrow(() -> new UpstreamServiceException("Failed to persist or read location"));
                            }
                        }

                        // find or create weather record for date with save-and-reread on unique constraint
                        var existing = weatherRecordRepository.findByLocationAndDate(loc, forDate);
                        WeatherRecord record = existing.orElse(null);
                        if (record == null) {
                            // choose endpoint based on date: current vs historical
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
                                    // concurrent insert; read the existing one
                                    record = weatherRecordRepository.findByLocationAndDate(loc, forDate).orElseThrow(() -> new UpstreamServiceException("Failed to persist or read weather record"));
                                }
                            } catch (UpstreamServiceException use) {
                                throw use;
                            } catch (Exception e) {
                                throw new UpstreamServiceException("Failed to parse weather response", e);
                            }
                        }

                        WeatherResponse resp = new WeatherResponse();
                        resp.setPincode(pincode);
                        resp.setPlace(loc.getPincode());
                        resp.setDate(forDate);
                        resp.setTemperature(record.getTemperatureC());
                        resp.setHumidity(record.getHumidity());
                        resp.setPressure(record.getPressure());
                        resp.setWindSpeed(record.getWindSpeed());
                        resp.setDescription(record.getDescription());

                        return resp;
                    }
                }
