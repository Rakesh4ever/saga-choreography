# Movie Booking Saga (Choreography)

A movie seat booking example built as three Spring Boot services. Services coordinate through Kafka events; the shared `movie-booking-commons` module contains the event and API DTO types.

## Services and technologies

| Component | Port | Implementation |
| --- | ---: | --- |
| `booking-service` | 9191 | Spring Boot 3.5.7, Java 21, Spring MVC, Spring Data JPA, MySQL, Spring Kafka, springdoc OpenAPI |
| `seat-inventory-service` | 9292 | Spring Boot 3.5.7, Java 21, Spring MVC, Spring Data JPA, MySQL, Spring Kafka |
| `payment-service` | 9393 | Spring Boot 3.5.7, Java 21, Spring Kafka; simulates payment processing (no payment database or external gateway) |
| `movie-booking-commons` | — | Shared Java DTOs/events and Kafka topic/group constants, built with Maven |

Infrastructure: MySQL on `localhost:3306` and Kafka on `localhost:9092`. Both database-backed services read the `javatechie` schema; configure the same MySQL username and password in each service's `application.yml`.

## Run all services

### 1. Install prerequisites

- JDK 21 (the Maven compiler targets Java 21)
- Maven 3.9 or later
- MySQL 8 (or a compatible MySQL server)
- Apache Kafka reachable at `localhost:9092`

The commands below are run from this project directory, which contains the service folders.

### 2. Start MySQL and prepare the schema

Start your local MySQL server and connect as a MySQL administrator. Create a dedicated application account:

```sql
CREATE DATABASE IF NOT EXISTS javatechie;
CREATE USER IF NOT EXISTS 'saga_app'@'localhost' IDENTIFIED BY 'saga_password';
GRANT ALL PRIVILEGES ON javatechie.* TO 'saga_app'@'localhost';
```

Set `spring.datasource.username` to `saga_app` and `spring.datasource.password` to `saga_password` in both `booking-service/src/main/resources/application.yml` and `seat-inventory-service/src/main/resources/application.yml` (or use your own database account and password). Hibernate creates/updates the entity tables at startup. `seat-inventory-service` has `data.sql` seed rows for sample shows and seats.

### 3. Start Kafka

Start Kafka locally with its listener available at `localhost:9092`. For example, with Homebrew on macOS:

```bash
brew install kafka
brew services start kafka
```

For another Kafka installation, configure its advertised listener so applications on this machine can connect to `localhost:9092`. The services publish to `movie-booking-events`, `seat-reserved-topic`, and `payment-events`; Kafka auto-creates topics if broker auto-creation is enabled.

### 4. Build the shared module and services

The modules are separate Maven projects, and both applications depend on the shared module. Install the shared artifact into your local Maven repository before building the applications:

```bash
mvn -f movie-booking-commons/pom.xml clean install
mvn -f booking-service/pom.xml clean package -DskipTests
mvn -f seat-inventory-service/pom.xml clean package -DskipTests
mvn -f payment-service/pom.xml clean package -DskipTests
```

### 5. Start the three services

Open three terminal windows at the repository root and run one command in each:

```bash
java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar
```

```bash
java -jar seat-inventory-service/target/seat-inventory-service-0.0.1-SNAPSHOT.jar
```

```bash
java -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar
```

Wait for each application to report that it has started. Hibernate creates the tables when the two database-backed services start. The checked-in seat data is not loaded automatically by the current configuration; load it once after `seat-inventory-service` has created its table:

```bash
mysql -h127.0.0.1 -usaga_app -p javatechie < seat-inventory-service/src/main/resources/data.sql
```

Enter the database password when prompted. Booking API documentation is available at `http://localhost:9191/swagger-ui/index.html`.

### 6. Submit a sample booking

The sample seat rows are for `SHOW_101`, including available seats `A1`, `A2`, and `A5`. Submit a request with available seat IDs and an amount under 2000 for the simulated successful payment path:

```bash
curl -X POST http://localhost:9191/booking-service/bookSeat \
  -H 'Content-Type: application/json' \
  -d '{"reservationId":"demo-1","showId":"SHOW_101","seatIds":["A1"],"userId":"user-1","timestamp":"2026-10-08T12:00:00Z","amount":1500}'
```

The response reflects the booking service's persisted pending booking. Follow the logs of all services to see the asynchronous saga complete. A successful reservation flows through booking creation, seat locking, and simulated payment. Amounts above 2000 trigger the simulated payment failure and seat release path. Existing seed rows marked `LOCKED` or `RESERVED` cannot be selected as available.

## How the choreography is implemented

1. `booking-service` accepts `POST /booking-service/bookSeat`, persists a booking with pending status, and publishes a `BookingCreatedEvent` to `movie-booking-events`.
2. `seat-inventory-service` consumes that event, checks requested seats, marks available seats locked for the booking, and publishes a `SeatReservedEvent` to `seat-reserved-topic` with the result.
3. `payment-service` consumes successful seat reservation events. It simulates success for amounts at or below 2000 and failure above 2000, publishing `BookingPaymentEvent` results on `payment-events`.
4. `seat-inventory-service` consumes payment results. It keeps successfully paid seats locked or releases them after payment failure, then reports a failed reservation to `seat-reserved-topic`. `booking-service` consumes seat reservation results and marks the booking failed when seat reservation fails.

The event topics, consumer groups, event records, and booking request/response records are defined in `movie-booking-commons`. Event handling is asynchronous, so the HTTP response does not wait for the saga to finish. In the current implementation a successful booking is not updated from `PENDING` to a terminal status; the booking listener logs the successful seat reservation, while the failure path updates the booking to `FAILED`.

## Stop services

Stop each Spring Boot process with `Ctrl+C`. If Kafka was started with Homebrew, run `brew services stop kafka`. Stop MySQL using the method used to start it.
