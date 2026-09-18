# m321-processing-service

**Owner:** Levi
**Part of:** M321 project work on distributed systems (two independent repositories: this one, and Noah's `m321-service-noah`)

## Zweck

Der Processing Service verarbeitet Bestellungen asynchron, nachdem Noahs Order Service sie über
RabbitMQ als `OrderCreated`-Event veröffentlicht hat. Nach der (simulierten) Verarbeitung
publiziert dieser Service ein `OrderProcessed`-Event, das Noah wieder konsumiert.

## Verantwortungsgrenze

- Dieses Repository enthält **ausschließlich** den Processing Service. Es besitzt keine
  Abhängigkeit auf Noahs Java-Code, keine gemeinsame Datenbank und ruft Noah im
  Hauptworkflow nicht synchron per HTTP auf.
- Geteilt werden nur formale Contracts (AsyncAPI, siehe [`docs/asyncapi.yaml`](docs/asyncapi.yaml))
  und Deployment-Konfiguration (RabbitMQ-Topologie-Namen).
- Eigene DTOs (`model/OrderCreatedEvent`, `model/OrderProcessedEvent`) werden aus dem Contract
  abgeleitet, nicht aus Noahs Code importiert.

### Contract Status (wichtig)

Zum Zeitpunkt der Erstellung dieses Services lag **kein** Shared-Contracts-Repository und **kein**
`LEVI HANDOFF`-Abschnitt mit verbindlichen Exchange-/Queue-/Routing-Key-/Schema-Namen vor. Die in
diesem Repository verwendeten Namen (siehe unten) sind daher eine **dokumentierte, lokale
Annahme** dieses Repositories, festgehalten in [`docs/asyncapi.yaml`](docs/asyncapi.yaml).

Sobald ein echter Shared Contract (AsyncAPI aus einem gemeinsamen Contracts-Repo oder von Noah)
existiert, ist **dieser** die Source of Truth. In diesem Fall:

1. `docs/asyncapi.yaml` durch den echten Contract ersetzen bzw. abgleichen.
2. Die Werte in `src/main/resources/application.properties` (`app.messaging.*`) an den echten
   Contract anpassen.
3. Keine neuen, inkompatiblen Namen erfinden.

## Architektur

```
Client
   |
   v
Noah Order Service  --REST-->  (Order angenommen)
   |
   | publiziert OrderCreated
   v
RabbitMQ (Exchange: order.events)
   |
   | Queue: processing-service.order-created.queue
   v
Levi Processing Service   <-- dieses Repository
   |
   | publiziert OrderProcessed
   v
RabbitMQ (Exchange: order.events)
   |
   v
Noah Order Service (konsumiert OrderProcessed)
```

## RabbitMQ-Kommunikation

| Zweck                          | Name                                          |
|--------------------------------|------------------------------------------------|
| Exchange (topic, durable)      | `order.events`                                 |
| Routing Key `OrderCreated`     | `order.created`                                |
| Routing Key `OrderProcessed`   | `order.processed`                              |
| Queue (dieser Service, durable)| `processing-service.order-created.queue`       |

Dieser Service **deklariert** den Exchange sowie seine eigene, durable Queue inkl. Binding auf
den Routing Key `order.created` (siehe `config/RabbitMQConfig`). Die Queue, in die
`OrderProcessed` läuft, gehört Noah und wird dort deklariert — dieser Service publiziert nur auf
den Exchange mit dem Routing Key `order.processed`.

Alle Namen sind über `app.messaging.*` in `application.properties` konfigurierbar.

## Konsumiertes Schema: `OrderCreated`

```json
{
  "eventId": "e1a2b3c4-0000-0000-0000-000000000001",
  "correlationId": "corr-abc-123",
  "orderId": "order-456",
  "product": "Widget",
  "quantity": 2,
  "createdAt": "2026-09-18T10:00:00Z",
  "eventVersion": "1.0"
}
```

DTO: `model/OrderCreatedEvent`.

## Publiziertes Schema: `OrderProcessed`

```json
{
  "eventId": "f9e8d7c6-0000-0000-0000-000000000099",
  "correlationId": "corr-abc-123",
  "orderId": "order-456",
  "processedAt": "2026-09-18T10:00:03Z",
  "status": "PROCESSED",
  "eventVersion": "1.0"
}
```

DTO: `model/OrderProcessedEvent`. `eventId` ist **immer neu** (unabhängig von der `eventId` des
`OrderCreated`-Events), `correlationId` wird **unverändert** aus dem `OrderCreated`-Event
übernommen.

## Idempotenz

RabbitMQ garantiert *at-least-once delivery* — dieselbe Nachricht kann mehrfach zugestellt
werden. `service/IdempotencyStore` merkt sich bereits verarbeitete `eventId`s der
`OrderCreated`-Events und verhindert doppelte fachliche Verarbeitung
(`service/OrderProcessingService`).

- Aktuell: thread-sicherer In-Memory-`Set<String>` (`ConcurrentHashMap.newKeySet()`).
- **Ausdrücklich nur für dieses Schulprojekt ausreichend.** Bei mehreren horizontal skalierten
  Instanzen hat jede Instanz ihren eigenen Speicher — Idempotenz gilt dann nur pro Instanz, nicht
  service-weit. In Produktion braucht es einen persistenten, geteilten Store (z. B.
  Datenbanktabelle mit Unique Constraint auf `eventId`, oder Redis).
- Ebenfalls dokumentiert im Code: die `eventId` wird *vor* der fachlichen Verarbeitung als
  "gesehen" markiert, um parallele Doppelverarbeitung zu verhindern. Schlägt die Verarbeitung
  danach fehl, wird eine erneute Zustellung als Duplikat übersprungen statt retried — auch das
  wäre in Produktion über einen Status pro `eventId` (PENDING/DONE) zu lösen.

## Correlation-ID / Observability

- Die `correlationId` aus jedem `OrderCreated`-Event wird für die gesamte Verarbeitung in den
  SLF4J MDC-Kontext gelegt (`observability/LoggingContext`), zusammen mit `eventId`, `orderId`
  und dem Servicenamen.
- Einheitliches Log-Format (siehe `logback-spring.xml`):
  ```
  2026-09-18 10:00:00.000 INFO  [processing-service] eventId=... correlationId=... orderId=... - com.example... - status=RECEIVED message="Processing started"
  ```
- Dieselbe `correlationId` wird unverändert im publizierten `OrderProcessed`-Event mitgegeben, so
  dass ein Auftrag über Noah -> RabbitMQ -> Levi -> RabbitMQ -> Noah nachverfolgbar bleibt.

## Tolerant Reader

`OrderCreatedEvent`/`OrderProcessedEvent` sind mit `@JsonIgnoreProperties(ignoreUnknown = true)`
annotiert, zusätzlich ist `FAIL_ON_UNKNOWN_PROPERTIES` global deaktiviert
(`config/JacksonConfig`). Ergänzt Noah später z. B. ein Feld `customerReference`, bricht dieser
Consumer nicht — solange die hier benötigten Felder weiterhin vorhanden und kompatibel sind.
Siehe Test `OrderCreatedEventJsonTest`.

## Skalierung (Competing Consumers)

Der Service ist zustandslos genug, um mehrfach gleichzeitig gegen dieselbe RabbitMQ-Queue zu
laufen — RabbitMQ verteilt die Nachrichten der Queue `processing-service.order-created.queue`
per Round-Robin auf alle verbundenen Consumer-Instanzen (`@RabbitListener`, Spring AMQP
Default-Verhalten). Keine Instanz geht davon aus, die einzige zu sein.

Zwei Instanzen lokal starten (unterschiedliche Ports über Env-Variable):

```bash
SERVER_PORT=8081 ./mvnw spring-boot:run
SERVER_PORT=8082 ./mvnw spring-boot:run
```

Beide verbinden sich mit derselben Queue; Nachrichten werden zwischen ihnen aufgeteilt.

## Resilienz

Fällt dieser Service aus, bleibt Noahs Order-Annahme unberührt: Noah publiziert `OrderCreated`
weiterhin in den Exchange, RabbitMQ hält die Nachrichten in der durable Queue
`processing-service.order-created.queue` (Status `Ready` im RabbitMQ-Management-UI) vor, bis
dieser Service wieder verfügbar ist und sie nachträglich verarbeitet.

**Live-Demo:**

1. RabbitMQ starten.
2. Noahs Order Service starten.
3. Diesen Processing Service **nicht** starten.
4. Über Noah mehrere Bestellungen anlegen.
5. Im RabbitMQ-Management-UI die Queue `processing-service.order-created.queue` prüfen —
   Nachrichten stehen auf `Ready`.
6. Diesen Processing Service starten (`./mvnw spring-boot:run`).
7. Beobachten, wie die Queue leer läuft und pro Nachricht Log-Zeilen mit
   `status=RECEIVED` … `status=COMPLETED` erscheinen.
8. In Noahs Service/Queue prüfen, dass die entsprechenden `OrderProcessed`-Events ankommen.

## Security / Service-Grenze

Der Hauptworkflow dieses Services läuft ausschließlich über RabbitMQ und hat keine öffentliche
Benutzer-Order-API — daher wird hier kein OIDC/Keycloak-Login eingebaut. Keycloak sichert bei
Bedarf Noahs öffentliche Order-API, nicht diesen internen Verarbeitungsdienst.

Der einzige HTTP-Endpoint ist Spring Boot Actuator Health (`/actuator/health`), ohne Details
(`management.endpoint.health.show-details=never`), nur zur Erreichbarkeitsprüfung durch
Orchestrierung/Monitoring. In einer echten Produktionsumgebung sollte dieser Endpoint zusätzlich
netzwerkseitig (z. B. nicht öffentlich erreichbar) abgesichert werden.

RabbitMQ-Zugangsdaten stehen nicht im Code, sondern ausschließlich in
`application.properties` als Platzhalter mit Environment-Variable-Override
(`RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, …).

## Startanleitung

Voraussetzungen: Java 17, laufender RabbitMQ-Broker (Standard: `localhost:5672`, `guest`/`guest`).

```bash
./mvnw spring-boot:run
```

## Konfiguration

Alle Werte sind per `application.properties` bzw. Environment-Variablen überschreibbar:

| Environment-Variable   | Property                     | Default              |
|-------------------------|------------------------------|-----------------------|
| `SERVER_PORT`           | `server.port`                | `8081`                |
| `SERVICE_NAME`          | `processing.service-name`    | `processing-service`  |
| `RABBITMQ_HOST`         | `spring.rabbitmq.host`       | `localhost`           |
| `RABBITMQ_PORT`         | `spring.rabbitmq.port`       | `5672`                |
| `RABBITMQ_USERNAME`     | `spring.rabbitmq.username`   | `guest`               |
| `RABBITMQ_PASSWORD`     | `spring.rabbitmq.password`   | `guest`               |
| `PROCESSING_TIME_MS`    | `processing.time-ms`         | `2500`                |

## Docker

```bash
docker build -t m321-processing-service .
docker run -e RABBITMQ_HOST=rabbitmq -e SERVER_PORT=8081 -p 8081:8081 m321-processing-service
```

Gedacht zum gemeinsamen Start zusammen mit Noahs Order Service, RabbitMQ und Keycloak über das
gemeinsame Deployment (z. B. docker-compose) — keine Kubernetes-Konfiguration in diesem
Repository.

## Testanleitung

```bash
./mvnw test
```

Die Tests benötigen keine laufende externe Infrastruktur (kein echter RabbitMQ-Broker nötig):

- `OrderProcessingServiceTest` — Verarbeitung, `OrderProcessed`-Erzeugung, unveränderte
  `correlationId`, neue `eventId`, Duplicate-Handling (Mockito-Mocks für Publisher).
- `IdempotencyStoreTest` — Kernverhalten der Idempotenz-Komponente isoliert.
- `OrderCreatedEventJsonTest` — Tolerant Reader: unbekannte JSON-Felder brechen die
  Deserialisierung nicht.

## Live-Demo (Kurzfassung)

1. RabbitMQ + Noah + dieser Service starten.
2. Bestellung über Noahs API anlegen.
3. In den Logs dieses Services `status=RECEIVED` … `status=COMPLETED` mit gleicher
   `correlationId` beobachten.
4. In Noahs Logs/Queue das zugehörige `OrderProcessed`-Event mit derselben `correlationId`
   beobachten.
5. Für die Resilienz-Demo und Skalierungs-Demo siehe Abschnitte oben.
#   m 3 2 1 - p r o c e s s i n g - s e r v i c e  
 