package com.example.weather;

import com.example.weather.dto.WeatherRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WeatherControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void smokeTestEndpoint() {
        String url = "http://localhost:" + port + "/api/v1/weather";
        WeatherRequest req = new WeatherRequest();
        req.setPincode("411014");
        req.setForDate(LocalDate.of(2020,10,15));

        HttpHeaders headers = new HttpHeaders();
        HttpEntity<WeatherRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
