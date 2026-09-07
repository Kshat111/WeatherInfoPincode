package com.example.weather;

import com.example.weather.adapters.GeocodeResult;
import com.example.weather.adapters.OpenWeatherClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WeatherControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private OpenWeatherClient openWeatherClient;

    @Test
    public void smokeTestEndpoint_get() {
        when(openWeatherClient.geocodeByPincode("411014"))
                .thenReturn(new GeocodeResult(18.5, 73.8, "Pune"));
        when(openWeatherClient.getCurrentWeather(anyDouble(), anyDouble()))
                .thenReturn("{\"weather\":[{\"description\":\"clear sky\"}],\"main\":{\"temp\":25.0,\"humidity\":60,\"pressure\":1012},\"wind\":{\"speed\":1.5}}");

        String url = "http://localhost:" + port + "/api/v1/weather?pincode=411014&for_date=" + java.time.LocalDate.now().toString();
        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful())
            .as("status=%s body=%s", resp.getStatusCode(), resp.getBody())
            .isTrue();
    }
}
