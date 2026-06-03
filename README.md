# Fee Engine Service

Fee calculation engine for the `pisp` payment platform. Determines charges for payment transactions based on payment type, scheme, charge bearer, currency, and account — using a Drools rules engine with salience-based priority (FLAT > PERCENTAGE > TIERED > FREE). Supports all seven OB payment types, account-specific rule overrides, and `Shared` charge bearer decomposition into separate debtor/creditor lookups.

Part of the `pisp` platform alongside the [domestic-payments](../domestic-payments), [file-payments](../file-payments), and [saga-orchestrator](../saga-orchestrator) services.

For detailed sequence and flow diagrams covering fee calculation, admin CRUD, dry-run, security chains, and error handling, see [docs/fee-engine-flow-diagrams.md](docs/fee-engine-flow-diagrams.md).

## Endpoints

### Public (unauthenticated)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/fee-calculations` | Calculate fees for a payment request |

### Admin (JWT bearer required)

| Method | Path | Required scope | Description |
|--------|------|---------------|-------------|
| `GET` | `/admin/fee-rules` | `fee-rules:read` | List rules with filtering and pagination |
| `GET` | `/admin/fee-rules/{id}` | `fee-rules:read` | Get a single rule by ID |
| `POST` | `/admin/fee-rules` | `fee-rules:write` | Create a new fee rule |
| `PUT` | `/admin/fee-rules/{id}` | `fee-rules:write` | Replace a fee rule |
| `PATCH` | `/admin/fee-rules/{id}/status` | `fee-rules:write` | Toggle a rule's active status |
| `POST` | `/admin/fee-rules/dry-run` | `fee-rules:write` | Preview charges for a transient rule without persisting |

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
  exception/         FeeRuleNotFoundException
application/
  port/in/           CalculateFeesUseCase, ManageFeeRulesUseCase, DryRunFeeCalculationUseCase
  port/out/          FeeRuleRepository, FeeRuleDetails (shared output record)
  service/           CalculateFeesService — orchestrates lookup, Drools firing, deduplication
                     ManageFeeRulesService — CRUD + status toggle with optimistic-lock guard
                     DryRunFeeCalculationService — transient rule preview (no persistence)
adapter/
  in/rest/           FeeCalculationController — POST /fee-calculations
                     dto/ — FeeCalculationRequest, FeeCalculationResponse
  in/rest/admin/     FeeRuleAdminController — CRUD + status toggle
                     DryRunFeeCalculationController — POST /admin/fee-rules/dry-run
                     dto/ — CreateFeeRuleRequest, UpdateFeeRuleRequest, StatusToggleRequest,
                             FeeRuleRequest, FeeRuleResponse, FeeRulePageResponse,
                             DryRunRequest, DryRunResponse, TierDto, FeeRuleDtoMapper
  out/persistence/   FeeRuleRepositoryAdapter — JPA lookup + account override logic
                     jpa/ — FeeRuleEntity, FeeRuleJpaRepository
infrastructure/
  drools/            KieSessionConfig — stateful Drools session from classpath
  error/             GlobalExceptionHandler — RFC 7807 ProblemDetail responses
  security/          SecurityConfig — two ordered filter chains; AuditorAwareImpl (JWT sub → createdBy/updatedBy)
  validation/        FeeRuleRequestValidator, @ValidFeeRule — cross-field fee-type validation
  config/            JpaAuditingConfig
```

## Security

Spring Security is configured with two ordered filter chains:

| Chain | Matcher | Auth |
|-------|---------|------|
| `calculationChain` (`@Order(1)`) | `/fee-calculations/**`, `/actuator/**` | None — fully open |
| `adminChain` (`@Order(2)`) | `/admin/**` | JWT bearer (OAuth2 resource server). GET → `SCOPE_fee-rules:read`; POST/PUT/PATCH → `SCOPE_fee-rules:write` |

Any path not matched by the first two chains is denied by a default `denyAll` chain. Unauthenticated or forbidden requests to `/admin/**` receive an `application/problem+json` response with status 401 or 403.

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

### Indexes & Unique Constraints

- **`idx_fee_rules_lookup`** — Partial index on `(payment_type, scheme, charge_bearer, currency, account_identification) WHERE active = TRUE`
- **`uniq_active_fee_rules`** — Unique partial index enforcing at most one active rule per `(payment_type, scheme, charge_bearer, account_identification)` combination. Violation returns HTTP 409 with `"Active fee rule already exists for this combination"`.

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
| Application unit | CalculateFeesService, ManageFeeRulesService, DryRunFeeCalculationService with mocked repository | Mockito |
| Persistence slice | FeeRuleRepositoryAdapter — account override, constraint enforcement | `@DataJpaTest` + Testcontainers PostgreSQL |
| REST slice — calculation | FeeCalculationController — validation, error handling | `@WebMvcTest` |
| REST slice — admin | FeeRuleAdminController, DryRunFeeCalculationController — auth (scopes), CRUD, 404/409 | `@WebMvcTest` + `@Import(SecurityConfig.class)`, `jwt().authorities(...)` |
| Validator unit | FeeRuleRequestValidator — per-fee-type field rules | Zero Spring context |
| Drools unit | Fee rules (flat, percentage, tiered, free) — correct charges, rounding, boundary cases | `DroolsTestSupport` + real KIE session |

Coverage targets: 90%+ branch on `domain`, 80%+ on `application`. Enforced via JaCoCo.

## Error Responses

All errors return RFC 7807 `ProblemDetail` (`application/problem+json`).

| Scenario | HTTP | Detail |
|----------|------|--------|
| Missing required request field | 400 | `Request validation failed` |
| Invalid parameter value (bad currency, unknown enum) | 400 | `Invalid request parameter value` |
| Invalid fee rule fields (e.g. FREE with amount) | 400 | `Request validation failed` (via `@ValidFeeRule`) |
| Corrupt rule configuration (invalid tier bounds, null tier field) | 500 | `Fee calculation failed due to invalid rule configuration` |
| Rule not found | 404 | `Fee rule {id} not found` |
| Duplicate active rule for same combination | 409 | `Active fee rule already exists for this combination` |
| Concurrent update (stale version) | 409 | `Concurrent update detected. Refresh and retry.` |
| Missing or invalid JWT on `/admin/**` | 401 | `Unauthorized` |
| Insufficient scope on `/admin/**` | 403 | `Access Denied` |

## Related

- [domestic-payments](../domestic-payments) — calls fee-engine for domestic payment fee calculation
- [file-payments](../file-payments) — calls fee-engine for file payment fee calculation
- [saga-orchestrator](../saga-orchestrator) — orchestrates payment workflows that may trigger fee lookups
- [event-notification](../event-notification) — event notification service for SET delivery
