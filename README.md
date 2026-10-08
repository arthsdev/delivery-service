# delivery-service

![Java](https://img.shields.io/badge/Java-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-Aiven-4479A1?logo=mysql&logoColor=white)
![Redis Streams](https://img.shields.io/badge/Redis_Streams-Upstash-DC382D?logo=redis&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-migrations-CC0200?logo=flyway&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white)
![OpenAPI](https://img.shields.io/badge/OpenAPI-Swagger_UI-85EA2D?logo=swagger&logoColor=black)
![Testcontainers](https://img.shields.io/badge/Tests-Mockito_%2B_Testcontainers-2496ED?logo=docker&logoColor=white)
![Oracle Cloud](https://img.shields.io/badge/Deployed_on-Oracle_Cloud-F80000?logo=oracle&logoColor=white)

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

`DeliveryStreamConsumer` creates the consumer group (reading from the beginning of the stream) and registers the listener with `lastConsumed()`. If the group already exists, Redis answers `BUSYGROUP`; the consumer recognizes it by walking the exception's cause chain and continues. Any other error stops the startup.

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

### Known gaps

Pending messages are not redelivered, a crash between `create()` and `XACK` can create a duplicate `DeliveryAttempt`, and a Redis outage at boot prevents the service from starting. All three are mapped to experiments in the main README.

---

## Configuration

| Variable | Purpose |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | this service's MySQL |
| `REDIS_URL` | `rediss://...` URL, the **same Redis instance** as the webhook-service |
| `EVENT_STREAM_DELIVERIES_KEY` | optional; must match the webhook-service |
| `EVENT_STREAM_CONSUMER_NAME` | optional; identifies this instance inside the group (default `delivery-service-1`) |

The service listens on port **8081**. Swagger UI: `http://localhost:8081/swagger-ui.html`.

## Running and testing

```bash
cp .env.example .env   # fill in the values
mvn test               # unit tests (Mockito) and integration tests (Testcontainers, real MySQL)
mvn spring-boot:run
```

In production it runs as a jar under `systemd` on a 1 vCPU / 1 GB VM with `-Xmx400m`. Booting takes about a minute there, and the first `/v3/api-docs` request takes a few seconds before it is cached.

## Stack

Java, Spring Boot 4.1 (Spring MVC, Spring Data JPA, Spring Data Redis), MySQL on Aiven, Flyway, SpringDoc OpenAPI, Mockito, Testcontainers, Maven.