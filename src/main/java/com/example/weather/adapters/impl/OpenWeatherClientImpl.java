package com.example.weather.adapters.impl;

import com.example.weather.adapters.GeocodeResult;
import com.example.weather.adapters.OpenWeatherClient;
import com.example.weather.config.OpenWeatherProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Component
public class OpenWeatherClientImpl implements OpenWeatherClient {

    private static final Logger log = LoggerFactory.getLogger(OpenWeatherClientImpl.class);

    private final RestTemplate restTemplate;
    private final OpenWeatherProperties properties;
    private final ObjectMapper mapper;

    public OpenWeatherClientImpl(RestTemplate restTemplate, OpenWeatherProperties properties, ObjectMapper mapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public GeocodeResult geocodeByPincode(String pincode) {
        try {
            String country = properties.getDefaultCountry();
            String url = String.format("http://api.openweathermap.org/geo/1.0/zip?zip=%s,%s&appid=%s", pincode, country, properties.getApiKey());
            ResponseEntity<String> resp = restTemplate.getForEntity(new URI(url), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("Geocode call failed: " + resp.getStatusCode());
            }
            JsonNode root = mapper.readTree(resp.getBody());
            double lat = root.path("lat").asDouble();
            double lon = root.path("lon").asDouble();
            String name = root.path("name").asText(null);
            return new GeocodeResult(lat, lon, name);
        } catch (Exception e) {
            log.warn("Geocoding failed for pincode {}: {}", pincode, e.getMessage());
            throw new RuntimeException("Geocoding failed", e);
        }
    }

    @Override
    public String getCurrentWeather(double latitude, double longitude) {
        try {
            String url = String.format("https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=%s&units=metric", latitude, longitude, properties.getApiKey());
            ResponseEntity<String> resp = restTemplate.getForEntity(new URI(url), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("Weather call failed: " + resp.getStatusCode());
            }
            return resp.getBody();
        } catch (Exception e) {
            log.warn("Weather fetch failed for {} {}, error: {}", latitude, longitude, e.getMessage());
            throw new RuntimeException("Weather fetch failed", e);
        }
    }
}
