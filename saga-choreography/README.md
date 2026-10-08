# Movie Booking Saga (Choreography)

A movie seat booking example built as three Spring Boot services. Services coordinate through Kafka events; the shared `movie-booking-commons` module contains the event and API DTO types.

## Services and technologies

| Component | Port | Implementation |
| --- | ---: | --- |
| `booking-service` | 9191 | Spring Boot 3.5.7, Java 21, Spring MVC, Spring Data JPA, MySQL, Spring Kafka, springdoc OpenAPI |
| `seat-inventory-service` | 9292 | Spring Boot 3.5.7, Java 21, Spring MVC, Spring Data JPA, MySQL, Spring Kafka |
| `payment-service` | 9393 | Spring Boot 3.5.7, Java 21, Spring Kafka; simulates payment processing (no payment database or external gateway) |
| `movie-booking-commons` | — | Shared Java DTOs/events and Kafka topic/group constants, built with Maven |

Infrastructure: MySQL on `localhost:3306` and Kafka on `localhost:9092`. Booking Service uses the `saga_choreography` schema, and Seat Inventory uses the `javatechie` schema. Provide each service valid MySQL credentials with access to its configured schema.

## Run all services manually

Run these commands from the project directory containing `booking-service`, `seat-inventory-service`, `payment-service`, and `movie-booking-commons`.

### 1. Install prerequisites

- JDK 21 (the Maven compiler targets Java 21)
- Maven 3.9 or later
- MySQL 8 or a compatible MySQL server
- Apache Kafka

On macOS with Homebrew, install missing infrastructure and start it:

```bash
brew install mysql kafka
brew services start mysql
brew services start kafka
```

For non-Homebrew installs, start MySQL and Kafka with the commands for your installation. The apps expect MySQL at `localhost:3306` and Kafka at `localhost:9092`.

### 2. Create the MySQL schemas and application account

Connect to MySQL as an administrator and run:

```sql
CREATE DATABASE IF NOT EXISTS saga_choreography;
CREATE DATABASE IF NOT EXISTS javatechie;
CREATE USER IF NOT EXISTS 'saga_app'@'localhost' IDENTIFIED BY 'saga_password';
GRANT ALL PRIVILEGES ON saga_choreography.* TO 'saga_app'@'localhost';
GRANT ALL PRIVILEGES ON javatechie.* TO 'saga_app'@'localhost';
```

The current configuration uses `saga_choreography` for Booking and `javatechie` for Seat Inventory. The username and password in the two checked-in YAML files differ, so pass the same working credentials as environment variables when launching both apps. Hibernate creates or updates their entity tables at startup.

### 3. Build the shared module and services

The services depend on the shared `movie-booking-commons` Maven artifact. Build and install it first:

```bash
mvn -f movie-booking-commons/pom.xml clean install -DskipTests
mvn -f booking-service/pom.xml clean package -DskipTests
mvn -f seat-inventory-service/pom.xml clean package -DskipTests
mvn -f payment-service/pom.xml clean package -DskipTests
```

### 4. Start each service in its own terminal

Start Booking Service:

```bash
SPRING_DATASOURCE_USERNAME=saga_app SPRING_DATASOURCE_PASSWORD=saga_password \
  java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar
```

Start Seat Inventory Service:

```bash
SPRING_DATASOURCE_USERNAME=saga_app SPRING_DATASOURCE_PASSWORD=saga_password \
  java -jar seat-inventory-service/target/seat-inventory-service-0.0.1-SNAPSHOT.jar
```

Start Payment Service:

```bash
java -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar
```

Use your actual MySQL username and password in place of `saga_app` and `saga_password`. Wait for Booking and Seat Inventory to connect to MySQL and for each app to report that it started. Kafka creates the event topics when the apps publish, provided broker topic auto-creation is enabled. Booking API documentation is at `http://localhost:9191/swagger-ui/index.html`.

### 5. Load sample seats

After Seat Inventory starts and Hibernate creates its table, load the sample rows once:

```bash
mysql -h127.0.0.1 -usaga_app -p javatechie < seat-inventory-service/src/main/resources/data.sql
```

Enter the same MySQL password when prompted. The SQL seeds seats for `SHOW_101`, `SHOW_202`, and `SHOW_303`.

### 6. Submit a sample booking

The sample seat rows are for `SHOW_101`, including available seats `A1`, `A2`, and `A5`. Submit a request with available seat IDs and an amount under 2000 for the simulated successful payment path:

```bash
curl -X POST http://localhost:9191/booking-service/bookSeat \
  -H 'Content-Type: application/json' \
  -d '{"reservationId":"demo-1","showId":"SHOW_101","seatIds":["A1"],"userId":"user-1","timestamp":"2026-10-08T12:00:00Z","amount":1500}'
```

The API responds with the booking record before the asynchronous saga finishes. The current mapper initially sets its status to `CONFIRMED`; a later seat or payment failure can change it to `FAILED`. Follow the logs of all services to observe the saga. Amounts above 2000 trigger the simulated payment failure and seat release path. Existing seed rows marked `LOCKED` or `RESERVED` cannot be selected as available.

## How the choreography is implemented

1. `booking-service` accepts `POST /booking-service/bookSeat`, persists a booking (initial status `CONFIRMED`), and publishes a `BookingCreatedEvent` to `movie-booking-events`.
2. `seat-inventory-service` consumes that event, checks requested seats, marks available seats locked for the booking, and publishes a `SeatReservedEvent` to `seat-reserved-topic` with the result.
3. `payment-service` consumes successful seat reservation events. It simulates success for amounts at or below 2000 and failure above 2000, publishing `BookingPaymentEvent` results on `payment-events`.
4. `seat-inventory-service` consumes payment results. It keeps successfully paid seats locked or releases them after payment failure, then reports a failed reservation to `seat-reserved-topic`. `booking-service` consumes seat reservation results and marks the booking failed when seat reservation fails.

The event topics, consumer groups, event records, and booking request/response records are defined in `movie-booking-commons`. Event handling is asynchronous, so the HTTP response does not wait for the saga to finish. The booking's initial `CONFIRMED` status is written before the seat and payment checks finish; the booking listener marks it `FAILED` when it receives a failed seat reservation event.

## Postman collection

Import [`postman/saga-choreography.postman_collection.json`](postman/saga-choreography.postman_collection.json) into Postman. It contains success and payment failure examples against the booking endpoint. The seat inventory and payment services do not expose HTTP endpoints; the booking calls trigger their Kafka flows.

## Run from IntelliJ IDEA

Import each service POM (`booking-service/pom.xml`, `seat-inventory-service/pom.xml`, `payment-service/pom.xml`, and `movie-booking-commons/pom.xml`) as a Maven project and reload Maven projects. Create an **Application** run configuration for each service with a JDK 21 SDK, the matching module on the classpath, and these main classes:

| Service | Main class |
| --- | --- |
| Booking | `com.kumar.ReservationServiceApplication` |
| Seat Inventory | `com.kumar.SeatInventoryServiceApplication` |
| Payment | `com.kumar.PaymentServiceApplication` |

Set the working directory to the matching service folder. Add `SPRING_DATASOURCE_USERNAME=saga_app` and `SPRING_DATASOURCE_PASSWORD=saga_password` under **Environment variables** for Booking and Seat Inventory (replace with your actual credentials). Payment does not need database variables. Start MySQL and Kafka before running the configurations.

## Stop services

Stop each Spring Boot process with `Ctrl+C`. If Kafka and MySQL were started with Homebrew, stop them with:

```bash
brew services stop kafka
brew services stop mysql
```
