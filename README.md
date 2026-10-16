# delivery-service

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Hibernate](https://img.shields.io/badge/Hibernate-JPA-59666B?logo=hibernate&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-Aiven-4479A1?logo=mysql&logoColor=white)
![Redis Streams](https://img.shields.io/badge/Redis_Streams-Upstash-DC382D?logo=redis&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-migrations-CC0200?logo=flyway&logoColor=white)
![OpenAPI](https://img.shields.io/badge/OpenAPI-Swagger_UI-85EA2D?logo=swagger&logoColor=black)
![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white)

![JUnit 5](https://img.shields.io/badge/JUnit_5-25A162?logo=junit5&logoColor=white)
![Mockito](https://img.shields.io/badge/Mockito-unit_tests-78A641)
![Testcontainers](https://img.shields.io/badge/Testcontainers-Docker-2496ED?logo=docker&logoColor=white)
![Linux](https://img.shields.io/badge/Linux-systemd-FCC624?logo=linux&logoColor=black)
![Oracle Cloud](https://img.shields.io/badge/Oracle_Cloud-OCI-F80000)

> The delivery half of the **Microservices Lab**: it receives delivery requests (over HTTP or from a Redis Stream), stores each one as a `DeliveryAttempt` and performs the HTTP delivery to the subscriber's URL.

> **Resumo (PT-BR):** segundo serviço do Microservices Lab. Recebe pedidos de entrega (por HTTP ou por um Redis Stream), grava cada um como uma tentativa de entrega no seu próprio banco e envia o payload para a URL do assinante. A visão geral do sistema e os experimentos estão no README principal, em inglês.

This is the second service of a small distributed system built to study what changes when an application stops being a single process. **The system overview, the architecture diagram, every experiment and the known limitations live in the main README:** [webhook-service](https://github.com/arthsdev/webhook-service#readme). This file only covers what is specific to this service.

---

## Responsibilities

- Own the `delivery_attempts` table in its **own MySQL database**, with its own Flyway migrations. It never reads the webhook-service's data.
- Accept delivery requests through two entry points that share the same use case, `DeliveryAttemptService.create()`:
  - **HTTP:** `POST /app/v1/deliveries` (synchronous path, called by the webhook-service)
  - **Redis Stream:** a consumer on `webhook.deliveries.requested` (asynchronous path)
- Deliver the payload to the target URL on an asynchronous executor, so a slow target does not block the caller or the stream reader.

### `delivery_attempts`

| Column | Meaning |
|---|---|
| `public_id` | public UUID of the attempt |
| `event_id`, `subscription_id` | public UUIDs from the webhook-service, **without foreign keys** |
| `target_url`, `payload` | copies of what must be delivered |
| `status`, `attempt_count`, `last_attempt_at`, `created_at` | outcome of the delivery |

---

## The Redis Stream consumer

### Message contract

> **Owned by the webhook-service. Keep in sync with its README.**

| Item | Value |
|---|---|
| Stream key | `webhook.deliveries.requested` (`event-stream.deliveries-key`) |
| Consumer group | `delivery-service` (`event-stream.group`) |
| Fields | `eventId`, `subscriptionId`, `targetUrl`, `payload`, `schemaVersion` |

### Startup

`DeliveryStreamConsumer` creates the consumer group (reading from the beginning of the stream) and registers the listener through an explicit read request: new messages only (`lastConsumed()`), manual `XACK` and no cancellation on error. If the group already exists, Redis answers `BUSYGROUP`; the consumer recognizes it by walking the exception's cause chain and continues. Any other error stops the startup.

### Per message

`DeliveryStreamListener` converts the fields into a `DeliveryRequest`, validates it with the **same constraints the HTTP endpoint uses**, calls `create()` and only then sends `XACK`.

| Situation | Action |
|---|---|
| `create()` succeeds | `XACK` |
| `create()` throws (database down, for example) | **no** `XACK`: the message stays pending |
| Malformed or invalid message (missing field, bad UUID, blank URL) | log an error, `XACK`, discard |
| `XACK` itself fails | log an error; the exception never leaves the listener |

### Design notes

- `XACK` means the delivery was **persisted**, not that it reached the target. `DeliveryProcessor` runs asynchronously after `create()` returns.
- The container uses a **10 s poll timeout** (`event-stream.poll-timeout`). It does not add latency, because a blocking read returns as soon as a message arrives; it only reduces the number of empty commands on the Redis free tier.
- The consumer can be switched off with `event-stream.consumer.enabled=false`, which the integration tests use so that they never need a Redis.
- **A Redis error while the service is running does not cancel the subscription:** the consumer logs one line, waits `event-stream.retry-pause` (default `3s`) and tries again, so it resumes on its own when Redis comes back (Experiment 5).

### Known gaps

Pending messages are not redelivered, a crash between `create()` and `XACK` can create a duplicate `DeliveryAttempt`, and a Redis outage at boot prevents the service from starting. All three are mapped to experiments in the main README.

While Redis is unreachable, `/actuator/health` can take about 60 s to answer, because this service does not set a Redis command timeout. The value has to stay above the poll timeout, so it is handled as a separate change.

---

## Configuration

| Variable | Purpose |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | this service's MySQL |
| `REDIS_URL` | `rediss://...` URL, the **same Redis instance** as the webhook-service |
| `EVENT_STREAM_DELIVERIES_KEY` | optional; must match the webhook-service |
| `EVENT_STREAM_CONSUMER_NAME` | optional; identifies this instance inside the group (default `delivery-service-1`) |

### Behavior flags (`application.yaml`)

| Property | Value | What it does |
|---|---|---|
| `spring.flyway.enabled` | `true` | Runs the Flyway migrations at startup. The schema is created and evolved by migrations only. |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate never changes the schema. It stops the startup if the entities do not match the migrated tables. |
| `spring.jpa.open-in-view` | `false` | No database session is kept open through the web layer. |
| `spring.jpa.show-sql` | `true` | Prints every SQL statement. Useful while running the experiments, noisy in production. |
| `spring.jpa.properties.hibernate.type.preferred_uuid_jdbc_type` | `CHAR` | UUIDs are mapped to `CHAR` columns. |
| `event-stream.consumer.enabled` | `true` (default) | Turns the Redis Stream consumer on or off. The integration tests set it to `false`. |
| `event-stream.poll-timeout` | `10s` | How long each blocking read waits when the stream is empty. A cost knob, not a latency knob. |
| `event-stream.retry-pause` | `3s` (default) | How long the consumer waits after a failed read before trying again. A read error no longer cancels the subscription. |
| `event-stream.group` | `delivery-service` | Consumer group name. Part of the contract with the webhook-service. |

The service listens on port **8081**. Swagger UI: `http://localhost:8081/swagger-ui.html`.

## Running and testing

```bash
mvn test               # unit tests (Mockito) and integration tests (Testcontainers, real MySQL; no Redis required)
mvn spring-boot:run    # needs the variables above in the environment
```

`.env.example` lists the variables to provide through the environment: exported in the shell, set in the IDE run configuration, or loaded with an `EnvironmentFile` under `systemd`, which is how it runs in production.

In production it runs as a jar under `systemd` on a 1 vCPU / 1 GB VM with `-Xmx400m`. Booting takes about a minute there, and the first `/v3/api-docs` request takes a few seconds before it is cached.

## Stack

Java 21, Spring Boot 4.1 (Spring MVC, Spring Data JPA with Hibernate, Spring Data Redis), MySQL on Aiven, Flyway, Redis Streams on Upstash, SpringDoc OpenAPI, JUnit 5, Mockito, Testcontainers, Maven.