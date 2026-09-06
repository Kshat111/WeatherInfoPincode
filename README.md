# Weather Service (Spring Boot) - Starter

This repository contains a minimal Spring Boot starter for the Weather Info assignment.

Commands:

```bash
mvn spring-boot:run
mvn test
```

The API exposes:

- POST /api/v1/weather  - accepts `pincode` and `forDate` (YYYY-MM-DD)

API endpoints
- GET /api/v1/weather?pincode=411014&for_date=2020-10-15

Note about historical data
- OpenWeather's free Current Weather endpoint returns current weather only. Historical dates (e.g., 2020-10-15) require the One Call "timemachine" endpoint which is part of a paid plan; the app will call the historical endpoint when `for_date` is in the past, but you need a paid OpenWeather key for historical data. For demo/testing use `for_date` = today with a free API key.
