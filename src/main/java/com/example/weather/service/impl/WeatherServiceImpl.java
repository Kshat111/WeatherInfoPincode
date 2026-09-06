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
                respCached.setLongitude(cachedNode.path("longitude").asDouble(0.0));
                respCached.setSource("cache");
                String fetchedAt = cachedNode.path("fetchedAt").asText(null);
                if (fetchedAt != null) respCached.setFetchedAt(OffsetDateTime.parse(fetchedAt));
                respCached.setDescription(cachedNode.path("description").asText(null));
                return respCached;
            } catch (Exception ex) {
                // ignore cache parse errors and continue to DB
            }
        }

        // Check existing weather record
        WeatherRecord record = weatherRecordRepository.findByLocationAndDate(loc, forDate).orElseGet(() -> {
            String lockKey = "lock:weather:" + loc.getPincode() + ":" + forDate.toString();
            boolean locked = lockService.tryLock(lockKey, 30);
            if (locked) {
                try {
                    // fetch from external API (current weather for now)
                    String raw = openWeatherClient.getCurrentWeather(loc.getLatitude(), loc.getLongitude());
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
                        WeatherRecord saved = weatherRecordRepository.save(wr);
                        // populate cache with small JSON
                        try {
                            String small = objectMapper.createObjectNode()
                                    .put("latitude", loc.getLatitude())
                                    .put("longitude", loc.getLongitude())
                                    .put("description", saved.getDescription() == null ? "" : saved.getDescription())
                                    .put("fetchedAt", saved.getFetchedAt().toString())
                                    .toString();
                            cacheService.put(cacheKey, small, 3600);
                        } catch (Exception ignore) {
                        }
                        return saved;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse weather response", e);
                    }
                } finally {
                    lockService.releaseLock(lockKey);
                }
            } else {
                // someone else is fetching; poll a few times for the record to appear
                int attempts = 5;
                for (int i = 0; i < attempts; i++) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    var opt = weatherRecordRepository.findByLocationAndDate(loc, forDate);
                    if (opt.isPresent()) {
                        return opt.get();
                    }
                }
                throw new RuntimeException("Failed to acquire lock and record not available");
            }
        });

        WeatherResponse resp = new WeatherResponse();
        resp.setPincode(pincode);
        resp.setForDate(forDate);
        resp.setLatitude(loc.getLatitude() == null ? 0.0 : loc.getLatitude());
        resp.setLongitude(loc.getLongitude() == null ? 0.0 : loc.getLongitude());
        resp.setSource("db");
        resp.setFetchedAt(record.getFetchedAt());
        resp.setDescription(record.getDescription());

        return resp;
    }
}
