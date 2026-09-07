package com.example.weather.adapters;

import com.example.weather.adapters.impl.OpenWeatherClientImpl;
import com.example.weather.config.OpenWeatherProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.http.HttpMethod.GET;

public class OpenWeatherClientImplTest {

    private RestTemplate restTemplate;
    private OpenWeatherProperties properties;
    private ObjectMapper mapper;
    private OpenWeatherClientImpl client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    public void setup() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        properties = new OpenWeatherProperties();
        properties.setApiKey("testkey");
        mapper = new ObjectMapper();
        client = new OpenWeatherClientImpl(restTemplate, properties, mapper);
    }

    @Test
    public void geocodeByPincode_parsesResponse() throws Exception {
        String body = "{ \"zip\": \"411014,IN\", \"name\": \"Pune\", \"lat\": 18.5, \"lon\": 73.8 }";
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("geo/1.0/zip")))
            .andExpect(method(GET))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        GeocodeResult res = client.geocodeByPincode("411014");
        assertThat(res).isNotNull();
        assertThat(res.getLatitude()).isEqualTo(18.5);
        assertThat(res.getLongitude()).isEqualTo(73.8);
        assertThat(res.getName()).isEqualTo("Pune");
        mockServer.verify();
    }

    @Test
    public void getCurrentWeather_returnsRawBody() throws Exception {
        String body = "{ \"weather\": [{\"description\": \"clear sky\"}], \"main\": {\"temp\": 25.3} }";
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("data/2.5/weather")))
            .andExpect(method(GET))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        String raw = client.getCurrentWeather(18.5, 73.8);
        assertThat(raw).contains("clear sky");
        mockServer.verify();
    }

    @Test
    public void getHistoricalWeather_returnsRawBody() throws Exception {
        String body = "{ \"current\": { \"weather\": [{\"description\": \"fog\"}], \"temp\": 20.0 } }";
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("onecall/timemachine")))
            .andExpect(method(GET))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        String raw = client.getHistoricalWeather(18.5, 73.8, 1602720000L);
        assertThat(raw).contains("fog");
        mockServer.verify();
    }
}
