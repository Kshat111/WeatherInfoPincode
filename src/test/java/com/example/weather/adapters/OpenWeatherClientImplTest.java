package com.example.weather.adapters;

import com.example.weather.adapters.impl.OpenWeatherClientImpl;
import com.example.weather.config.OpenWeatherProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OpenWeatherClientImplTest {

    private RestTemplate restTemplate;
    private OpenWeatherProperties properties;
    private ObjectMapper mapper;
    private OpenWeatherClientImpl client;

    @BeforeEach
    public void setup() {
        restTemplate = mock(RestTemplate.class);
        properties = new OpenWeatherProperties();
        properties.setApiKey("testkey");
        mapper = new ObjectMapper();
        client = new OpenWeatherClientImpl(restTemplate, properties, mapper);
    }

    @Test
    public void geocodeByPincode_parsesResponse() throws Exception {
        String body = "{ \"zip\": \"411014,IN\", \"name\": \"Pune\", \"lat\": 18.5, \"lon\": 73.8 }";
        when(restTemplate.getForEntity(any(java.net.URI.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        GeocodeResult res = client.geocodeByPincode("411014");
        assertThat(res).isNotNull();
        assertThat(res.getLatitude()).isEqualTo(18.5);
        assertThat(res.getLongitude()).isEqualTo(73.8);
        assertThat(res.getName()).isEqualTo("Pune");
    }

    @Test
    public void getCurrentWeather_returnsRawBody() throws Exception {
        String body = "{ \"weather\": [{\"description\": \"clear sky\"}], \"main\": {\"temp\": 25.3} }";
        when(restTemplate.getForEntity(any(java.net.URI.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        String raw = client.getCurrentWeather(18.5, 73.8);
        assertThat(raw).contains("clear sky");
    }
}
