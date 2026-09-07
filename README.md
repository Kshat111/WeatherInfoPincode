# Weather Service (Spring Boot) - Starter

This repository contains a Spring Boot REST service for the Weather Info assignment.

## Prerequisites

- Java 17 or newer and Maven.
- MySQL running locally or via Docker.
- A MySQL user with permission to create the `weatherdb` database. The application creates it automatically on startup when the user has `CREATE` privileges.

The runtime application uses MySQL. Set these environment variables before running `mvn spring-boot:run`:

- `DB_USERNAME` - MySQL username, default `root`.
- `DB_PASSWORD` - MySQL password, default `root`.
- `OPENWEATHER_API_KEY` - OpenWeather API key.

Tests use an isolated in-memory H2 database and do not require MySQL to be running.

Commands:

```bash
mvn spring-boot:run
mvn test
```

The API exposes:

- `GET /api/v1/weather?pincode=411014&for_date=2020-10-15`

Note about historical data
- OpenWeather's free Current Weather endpoint returns current weather only. Historical dates (e.g., 2020-10-15) require the One Call "timemachine" endpoint which is part of a paid plan; the app will call the historical endpoint when `for_date` is in the past, but you need a paid OpenWeather key for historical data. For demo/testing use `for_date` = today with a free API key.
