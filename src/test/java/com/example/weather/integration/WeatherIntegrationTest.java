package com.example.weather.integration;

import com.example.weather.repository.WeatherRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class WeatherIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:6-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        registry.add("spring.redis.host", () -> redis.getHost());
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        // set openweather api key to dummy — we'll mock RestTemplate
        registry.add("openweather.apiKey", () -> "testkey");
    }

    @Autowired
    private TestRestTemplate restTemplateClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private WeatherRecordRepository weatherRecordRepository;

    private MockRestServiceServer mockServer;

    @BeforeEach
    public void setup() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    public void endToEnd_flow_createsWeatherRecord() throws Exception {
        // mock geocode
        mockServer.expect(once(), requestTo(org.hamcrest.Matchers.containsString("geo/1.0/zip")))
                .andRespond(withSuccess("{ \"zip\": \"411014,IN\", \"name\": \"Pune\", \"lat\": 18.5, \"lon\": 73.8 }", MediaType.APPLICATION_JSON));

        // mock weather
        mockServer.expect(once(), requestTo(org.hamcrest.Matchers.containsString("data/2.5/weather")))
                .andRespond(withSuccess("{ \"weather\": [{\"description\": \"clear sky\"}], \"main\": {\"temp\": 25.3, \"humidity\": 60, \"pressure\": 1012}, \"wind\": {\"speed\": 1.5} }", MediaType.APPLICATION_JSON));

        String url = "/api/v1/weather";
        String body = "{\"pincode\": \"411014\", \"forDate\": \"2020-10-15\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplateClient.postForEntity(url, entity, String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        // ensure a weather record exists in DB
        var records = weatherRecordRepository.findAll();
        assertThat(records).isNotEmpty();
    }
}
