# Weather Info for Pincode

A single REST API that returns weather information for a given **pincode** and
**date**. Built with Spring Boot, backed by MySQL, and optimized so repeat
calls avoid unnecessary external API traffic.

This project was built against the following brief:

> Provide a single REST API for weather information for a particular day and
> a Pincode. Save pincode lat/long separately from the weather information.
> Optimize repeat calls so previously resolved data is reused instead of
> hitting external APIs again.

---

## How it works

```
GET /api/v1/weather?pincode=411014&for_date=2020-10-15
```

1. **Pincode → lat/long.** The service checks MySQL for a previously
   resolved location for this pincode. If none exists, it calls the
   [OpenWeather Geocoding API](https://openweathermap.org/api/geocoding-api)
   once and saves the result (latitude, longitude, place name). Every
   subsequent request for the same pincode reuses this row — the geocoding
   API is never called twice for the same pincode.

2. **Lat/long → weather.** The service checks MySQL for a previously fetched
   weather record for this exact `(pincode, date)` pair. If none exists, it
   calls the [OpenWeather Current Weather API](https://openweathermap.org/current)
   (or, for past dates, the historical/timemachine endpoint) once and saves
   the result. Every subsequent request for the same pincode and date is
   served straight from the database.

This two-level cache is what satisfies the "optimize for API calls"
requirement: a pincode is geocoded at most once, and weather for a given
pincode+date is fetched at most once.

---

## Data model

Two tables, matching the brief's explicit ask to store pincode lat/long
**separately** from weather information:

**`locations`** — one row per pincode
| Column | Description |
|---|---|
| `id` | Primary key |
| `pincode` | Unique, indexed |
| `latitude`, `longitude` | Resolved via Geocoding API |
| `place_name` | Human-readable location name |
| `source` | Which provider resolved this (`openweather`) |
| `created_at` | When first resolved |

**`weather_records`** — one row per `(pincode, date)`
| Column | Description |
|---|---|
| `id` | Primary key |
| `location_id` | Foreign key → `locations` |
| `date` | The `for_date` this record is for |
| `temperature_c`, `humidity`, `pressure`, `wind_speed`, `description` | Parsed weather fields |
| `raw_payload` | Full raw JSON response, kept for auditability |
| `fetched_at` | When this record was fetched |

A unique constraint on `(location_id, date)` is what actually enforces
"don't fetch this twice" at the database level, not just in application
logic — this makes the caching correct even under concurrent requests.

---

## Tech stack

| Concern | Choice |
|---|---|
| Framework | Spring Boot 3 (Java 17) |
| Persistence | Spring Data JPA + **MySQL** (RDBMS, as required) |
| External weather data | [OpenWeather API](https://openweathermap.org/current) — Geocoding + Current Weather |
| Validation | Jakarta Bean Validation |
| Testing | JUnit 5, Mockito, Spring's `MockRestServiceServer` and `TestRestTemplate` |
| Test database | H2 in-memory (isolated from the MySQL runtime config) |

No UI, no unrelated infrastructure — just what the brief asks for: a REST
API, an RDBMS, and tests.

---

## Getting started

### Prerequisites

- Java 17+
- Maven
- MySQL running locally (or via Docker) — a user with permission to create
  databases is enough; the app creates `weatherdb` automatically on first
  run
- A free [OpenWeather API key](https://openweathermap.org/api)

### Configuration

The app reads these as environment variables (see
`src/main/resources/application.yml` for defaults):

| Variable | Purpose | Default |
|---|---|---|
| `DB_USERNAME` | MySQL username | `root` |
| `DB_PASSWORD` | MySQL password | `root` |
| `OPENWEATHER_API_KEY` | Your OpenWeather API key | *(required, no default)* |

### Run it

```bash
export DB_USERNAME=root
export DB_PASSWORD=your-mysql-password
export OPENWEATHER_API_KEY=your-openweather-key

mvn spring-boot:run
```

The API is now live at `http://localhost:8080`.

### Swagger / OpenAPI

Swagger UI is available at:

- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/swagger-ui/index.html`

The generated OpenAPI document is available at:

- `http://localhost:8080/v3/api-docs`

Open Swagger UI, expand `GET /api/v1/weather`, click **Try it out**, enter a
pincode and today's date, then click **Execute**. Swagger documents and tests
the REST endpoint; it does not bypass OpenWeather's plan restrictions.

### Run the tests

```bash
mvn test
```

Tests use an isolated in-memory H2 database and mocked HTTP calls — **no
MySQL connection or real API key is required** to run the test suite.

---

## Try it

### cURL

```bash
curl "http://localhost:8080/api/v1/weather?pincode=411014&for_date=2026-09-07"
```

### Postman

- Method: `GET`
- URL: `http://localhost:8080/api/v1/weather`
- Params tab: `pincode` = `411014`, `for_date` = `2026-09-07`

### Sample response

```json
{
  "pincode": "411014",
  "place": "Pune",
  "date": "2026-09-07",
  "temperature": 29.6,
  "humidity": 73,
  "pressure": 1005,
  "windSpeed": 2.82,
  "description": "broken clouds"
}
```

### Seeing the optimization in action

Call the same `pincode` + `for_date` twice. The first call geocodes the
pincode and fetches weather from OpenWeather; the second call is served
entirely from MySQL — check the application logs and you'll see no outbound
calls to OpenWeather on the second request.

---

## API reference

### `GET /api/v1/weather`

| Query param | Type | Required | Notes |
|---|---|---|---|
| `pincode` | string | yes | 4–10 digits |
| `for_date` | string | yes | `yyyy-MM-dd` |

**Success — `200 OK`**
```json
{
  "pincode": "411014",
  "place": "Pune",
  "date": "2020-10-15",
  "temperature": 25.3,
  "humidity": 60,
  "pressure": 1012,
  "windSpeed": 1.5,
  "description": "clear sky"
}
```

**Errors**

| Status | Cause |
|---|---|
| `400 Bad Request` | Malformed `pincode` or `for_date` |
| `404 Not Found` | Pincode could not be resolved to a location |
| `502 Bad Gateway` | Upstream OpenWeather call failed |
| `500 Internal Server Error` | Unexpected server error |

All errors return a consistent JSON body:
```json
{
  "timestamp": "2026-09-07T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Could not resolve location for pincode: 000000",
  "path": "/api/v1/weather"
}
```

---

## A note on historical weather data

The assignment's sample input (`for_date: 2020-10-15`) is a past date.
OpenWeather's **free** Current Weather endpoint only returns *today's*
weather — historical lookups require their paid "One Call by Call"
subscription (the `timemachine` endpoint).

This service is built to call the correct endpoint automatically:
- `for_date` = today → free Current Weather API (works with a free key)
- `for_date` = any other date → historical/timemachine API (requires a paid
  OpenWeather key; returns `502 Bad Gateway` on a free key, since
  OpenWeather rejects the request rather than this service failing
  silently)

For a full, working demo with a free API key, use `for_date` = today's
date. The historical code path is implemented correctly and is ready to
work as soon as a paid key is supplied — nothing else needs to change.

---

## Project structure

```
src/main/java/com/example/weather/
├── adapters/          # OpenWeather API client (geocoding + weather)
├── api/                # REST controller + global exception handler
├── config/             # RestTemplate and OpenWeather properties
├── dto/                # Request/response shapes
├── exception/          # Typed exceptions (PincodeNotFound, UpstreamService)
├── model/               # JPA entities (Location, WeatherRecord)
├── repository/          # Spring Data JPA repositories
└── service/             # Core business logic (caching + orchestration)
```

Each layer has a single responsibility: the controller only validates and
delegates, the service owns the caching decisions, the adapter only knows
how to talk to OpenWeather, and the repositories only know how to talk to
the database.

---

## Design decisions worth calling out

- **Concurrency-safe caching without a lock service.** Two simultaneous
  requests for a brand-new pincode may both miss the cache and both call
  the Geocoding API — that's a harmless extra external call, not a
  correctness bug. The save itself is protected: a unique constraint at the
  database level rejects the second insert, and the service catches that
  and re-reads the row the other request just wrote, rather than erroring
  out.
- **GET, not POST.** This is a read/lookup operation, so it uses query
  parameters rather than a request body — this also makes it trivially
  testable from a browser address bar, `curl -G`, or Postman's URL bar
  without constructing JSON.
- **No caching layer beyond the database.** The brief asks for the
  information to be saved "in DB (RDBMS)" and optimized on repeat calls —
  the RDBMS itself is the cache. No additional infrastructure (Redis,
  in-memory caches) was introduced, keeping the solution scoped to what was
  asked.