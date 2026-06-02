# Fee Engine Service

Fee calculation engine for the `pisp` payment platform. Determines charges for payment transactions based on payment type, scheme, charge bearer, currency, and account — using a Drools rules engine with salience-based priority (FLAT > PERCENTAGE > TIERED > FREE). Supports all seven OB payment types, account-specific rule overrides, and `Shared` charge bearer decomposition into separate debtor/creditor lookups.

Part of the `pisp` platform alongside the [domestic-payments](../domestic-payments), [file-payments](../file-payments), and [saga-orchestrator](../saga-orchestrator) services.

## Endpoints

### Public

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/fee-calculations` | Calculate fees for a payment request |

### Actuator

| Path | Description |
|------|-------------|
| `/actuator/health` | Health check with datasource status |
| `/actuator/info` | Application info |

## Architecture

Hexagonal (ports + adapters) with `FeeRule` as the aggregate root.

```
domain/
  model/             FeeRule, FeeRequest, Charge, Tier, InstructedAmount, AccountRef
                     PaymentType (7 values), PaymentScheme (4), ChargeBearer (4), FeeType (4)
application/
  port/in/           CalculateFeesUseCase (interface + Command record)
  port/out/          FeeRuleRepository
  service/           CalculateFeesService — orchestrates lookup, Drools firing, deduplication
adapter/
  in/rest/           FeeCalculationController — POST /fee-calculations
                     dto/ — FeeCalculationRequest, FeeCalculationResponse (nested records)
  out/persistence/   FeeRuleRepositoryAdapter — JPA lookup + account override logic
                     jpa/ — FeeRuleEntity, FeeRuleJpaRepository
infrastructure/
  drools/            KieSessionConfig — stateful Drools session from classpath
  error/             GlobalExceptionHandler — RFC 7807 ProblemDetail responses
```

## Fee Calculation Flow

### Charge Bearer Handling

| ChargeBearer | Behaviour |
|---|---|
| `BorneByDebtor` | Looks up rules using debtor account identification |
| `BorneByCreditor` | Looks up rules using creditor account identification |
| `Shared` | Decomposes into two lookups: `BorneByDebtor` (debtor account) + `BorneByCreditor` (creditor account). Both rule sets fire in a single Drools session. |
| `FollowingServiceLevel` | Returns empty charges immediately — no rules apply. |

### Account-Specific Override

Rules can optionally target a specific account (`account_identification`). When a specific rule matches the request's account, it takes precedence over any-account (null `account_identification`) rules. This allows per-account fee customisation without overriding the default rule set.

### Drools Salience & Deduplication

Eight rules in `rules/fee-calculation.drl` fire based on `FeeType` × `ChargeBearer` × account presence:

| Salience | FeeType | Description |
|----------|---------|-------------|
| 30 | `FLAT` | Fixed amount charge |
| 20 | `PERCENTAGE` | Percentage of instructed amount, rounded `HALF_UP` to currency default fraction digits |
| 10 | `TIERED` | Amount looked up from tier ranges (`min ≤ amount < max`) |
| 5 | `FREE` | Zero-amount charge (no monetary fields stored) |

`CalculateFeesService` deduplicates by `chargeBearer:chargeType` key — first-seen wins, so higher-salience rules take precedence when multiple fee types match the same charge type and bearer.

## Tech Stack

- **Java 21** with virtual threads
- **Spring Boot 3.5.14** (Web, Data JPA, Actuator, Validation)
- **PostgreSQL 16** — default schema, Flyway migrations
- **Drools 10.2.0** — stateful KIE session for fee rule evaluation
- **Testcontainers 2.0.5** — PostgreSQL integration tests

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 16 (or run via Docker/Testcontainers for tests)
- Docker (for Testcontainers during `mvn test`)

## Build & Run

```bash
# Compile
mvn compile

# Run tests (Testcontainers spins up PostgreSQL automatically)
mvn test

# Run a single test class
mvn test -Dtest=CalculateFeesServiceTest

# Run the service (requires PostgreSQL)
mvn spring-boot:run
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/fee_engine` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `fee_engine` | Database user |
| `DB_PASSWORD` | `fee_engine` | Database password |

### Key Spring Properties

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/fee_engine}
    username: ${DB_USERNAME:fee_engine}
    password: ${DB_PASSWORD:fee_engine}
  jpa:
    hibernate:
      ddl-auto: validate            # Schema managed by Flyway only
    open-in-view: false
  flyway:
    locations: classpath:db/migration

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

## Database Schema

Flyway manages the schema. Single table:

- **`fee_rules`** — Fee rule definitions: typed columns (`payment_type`, `scheme`, `charge_bearer`, `charge_type`, `fee_type`, `flat_amount`, `percentage`, `currency`, `active`) + JSONB (`tiers`). Nullable `account_identification` for account-specific overrides.

### Check Constraints

| Constraint | Rule |
|---|---|
| `chk_charge_bearer` | `IN ('BorneByDebtor', 'BorneByCreditor')` — `Shared`/`FollowingServiceLevel` are never stored |
| `chk_percentage_range` | `percentage > 0 AND percentage <= 1` |
| `chk_flat_requires_amount` | `FLAT` fee type must have `flat_amount` |
| `chk_percentage_requires_rate` | `PERCENTAGE` fee type must have `percentage` |
| `chk_tiered_requires_tiers` | `TIERED` fee type must have `tiers` |
| `chk_flat_amount_positive` | `flat_amount` must be positive when present |
| `chk_free_no_amount` | `FREE` fee type must have null `flat_amount`, `percentage`, and `tiers` |

### Index

- **`idx_fee_rules_lookup`** — Partial index on `(payment_type, scheme, charge_bearer, currency, account_identification) WHERE active = TRUE`

### Migrations

| Version | Description |
|---------|-------------|
| V1 | Create `fee_rules` table with check constraints and partial index |
| V2 | Add `chk_free_no_amount` constraint as `NOT VALID` (zero-downtime) |
| V3 | `VALIDATE CONSTRAINT chk_free_no_amount` (runs off-peak under `ShareUpdateExclusiveLock`) |

## Testing

| Layer | Scope | Tooling |
|-------|-------|---------|
| Domain unit | FeeRule, Tier, value objects | JUnit 5 + AssertJ (zero Spring) |
| Application unit | CalculateFeesService with mocked repository, real Drools session | Mockito |
| Persistence slice | FeeRuleRepositoryAdapter — account override, constraint enforcement | `@DataJpaTest` + Testcontainers PostgreSQL |
| REST slice | FeeCalculationController — validation, error handling | `@WebMvcTest` |
| Drools unit | Fee rules (flat, percentage, tiered, free) — correct charges, rounding, boundary cases | `DroolsTestSupport` + real KIE session |

Coverage targets: 90%+ branch on `domain`, 80%+ on `application`. Enforced via JaCoCo.

## Error Responses

All errors return RFC 7807 `ProblemDetail` (`application/problem+json`).

| Scenario | HTTP | Detail |
|----------|------|--------|
| Missing required request field | 400 | `Request validation failed` |
| Invalid parameter value (bad currency, unknown enum) | 400 | `Invalid request parameter value` |
| Corrupt rule configuration (invalid tier bounds, null tier field) | 500 | `Fee calculation failed due to invalid rule configuration` |

## Related

- [domestic-payments](../domestic-payments) — calls fee-engine for domestic payment fee calculation
- [file-payments](../file-payments) — calls fee-engine for file payment fee calculation
- [saga-orchestrator](../saga-orchestrator) — orchestrates payment workflows that may trigger fee lookups
- [event-notification](../event-notification) — event notification service for SET delivery
