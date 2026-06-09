# Fee Engine Service

Fee calculation engine for the `pisp` payment platform. Determines charges for payment transactions based on payment type, scheme, charge bearer, currency, and account — using a Drools rules engine with salience-based priority (FLAT > PERCENTAGE > TIERED_SLAB / TIERED_STEP > FREE). Supports all seven OB payment types, account-specific rule overrides, `Shared` charge bearer decomposition, and tiered pricing in both slab (volume) and step (progressive bracket) modes with four per-tier formula types (FIXED, PERCENTAGE, HYBRID, GREATER_OF).

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
                     TierFormulaEvaluator — static compute(tier, txnAmount, currency) and
                       bracketAmount(tier, txnAmount); dispatches FIXED/PERCENTAGE/HYBRID/GREATER_OF
                     PaymentType (7 values), PaymentScheme (4), ChargeBearer (4),
                     FeeType (5: FLAT, PERCENTAGE, TIERED_SLAB, TIERED_STEP, FREE),
                     TierRateType (4: FIXED, PERCENTAGE, HYBRID, GREATER_OF)
  exception/         FeeRuleNotFoundException
application/
  port/in/           CalculateFeesUseCase, ManageFeeRulesUseCase, DryRunFeeCalculationUseCase
  port/out/          FeeRuleRepository, FeeRuleDetails (shared output record)
  service/           FeeSessionRunner — owns Drools session lifecycle for all callers;
                       registers dual globals (charges + tierContributions), fires rules,
                       deduplicates direct charges, accumulates TIERED_STEP contributions
                     TierContribution — intermediate record for STEP tier accumulation
                     CalculateFeesService — orchestrates lookup, delegates to FeeSessionRunner
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

Ten rules in `rules/fee-calculation.drl` fire per `FeeType` × `ChargeBearer` (one rule per combination per bearer):

| Salience | FeeType | Description |
|----------|---------|-------------|
| 30 | `FLAT` | Fixed amount charge |
| 20 | `PERCENTAGE` | Percentage of instructed amount, with optional `minFee`/`maxFee` caps; rounded `HALF_UP` |
| 10 | `TIERED_SLAB` | Finds the matching tier (`min ≤ amount < max`), applies its formula to the full amount |
| 10 | `TIERED_STEP` | Fires once per entered tier (`min < amount`); applies its formula to the bracket portion; contributions accumulated by `FeeSessionRunner` |
| 5 | `FREE` | Zero-amount charge (no monetary fields stored) |

`FeeSessionRunner` deduplicates direct charges by `chargeBearer:chargeType` key via `LinkedHashMap.putIfAbsent` (first-seen wins). TIERED_STEP tier contributions are grouped by the same key and summed before the same deduplication pass.

### Tiered Pricing Modes

| Mode | Distribution | DRL condition | Use case |
|------|-------------|---------------|----------|
| `TIERED_SLAB` | Whole amount gets the matching tier's rate | `min ≤ amount < max` | Volume pricing — large payments pay a lower flat/percentage rate |
| `TIERED_STEP` | Each bracket accumulates its portion | `min < amount` (strict) | Progressive pricing — like income tax brackets |

### Per-Tier Formula Types

Each tier in a rule has a `rateType`. Mixed types within one rule are allowed.

| `rateType` | Formula | Fields required |
|-----------|---------|-----------------|
| `FIXED` | `tier.amount` (ignores bracket size) | `amount > 0` |
| `PERCENTAGE` | `bracket × percentage` | `0 < percentage ≤ 1` |
| `HYBRID` | `tier.amount + bracket × percentage` | both `amount > 0` and `0 < percentage ≤ 1` |
| `GREATER_OF` | `max(tier.amount, bracket × percentage)` | both `amount > 0` and `0 < percentage ≤ 1` |

For `TIERED_SLAB`, "bracket" is the full transaction amount. For `TIERED_STEP`, "bracket" is `min(txnAmount, tier.max) − tier.min`.

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

- **`fee_rules`** — Fee rule definitions: typed columns (`payment_type`, `scheme`, `charge_bearer`, `charge_type`, `fee_type`, `flat_amount`, `percentage`, `min_fee`, `max_fee`, `currency`, `destination_country`, `priority`, `active`, audit columns) + JSONB `tiers`. Nullable `account_identification` for account-specific overrides. `tiers` JSONB schema: `[{min, max, rateType, amount?, percentage?}, ...]`.

### Check Constraints

| Constraint | Rule |
|---|---|
| `chk_charge_bearer` | `IN ('BorneByDebtor', 'BorneByCreditor')` — `Shared`/`FollowingServiceLevel` are never stored |
| `chk_percentage_range` | `percentage > 0 AND percentage <= 1` |
| `chk_flat_requires_amount` | `FLAT` fee type must have `flat_amount` |
| `chk_percentage_requires_rate` | `PERCENTAGE` fee type must have `percentage` |
| `chk_tiered_slab_requires_tiers` | `TIERED_SLAB` fee type must have `tiers` |
| `chk_tiered_step_requires_tiers` | `TIERED_STEP` fee type must have `tiers` |
| `chk_flat_amount_positive` | `flat_amount` must be positive when present |
| `chk_free_no_amount` | `FREE` fee type must have null `flat_amount`, `percentage`, and `tiers` |
| `chk_caps_only_percentage` | `min_fee`/`max_fee` only allowed on `PERCENTAGE` rules |
| `chk_destination_country_international_only` | `destination_country` only allowed on international payment types |
| `chk_priority_non_negative` | `priority >= 0` |

### Indexes & Unique Constraints

- **`idx_fee_rules_lookup`** — Partial index on `(payment_type, scheme, charge_bearer, currency, destination_country, account_identification) WHERE active = TRUE`
- **`uniq_active_fee_rules`** — Unique partial index enforcing at most one active rule per `(payment_type, scheme, charge_bearer, currency, COALESCE(destination_country,''), COALESCE(account_identification,''), charge_type)`. Violation returns HTTP 409 with `"Active fee rule already exists for this combination"`.

### Migrations

| Version | Description |
|---------|-------------|
| V1 | Create `fee_rules` table with check constraints and partial index |
| V2 | Add `chk_free_no_amount` constraint as `NOT VALID` (zero-downtime) |
| V3 | `VALIDATE CONSTRAINT chk_free_no_amount` |
| V4 | Add `version`, audit columns (`created_by`, `updated_by`), `uniq_active_fee_rules` |
| V5 | Add `min_fee`/`max_fee` + caps constraints |
| V6 | Add `destination_country VARCHAR(2)` for international types; rebuild lookup index |
| V7 | Add `priority INTEGER NOT NULL DEFAULT 0`; rebuild `uniq_active_fee_rules` to include `charge_type` |
| V8 | Rename `TIERED` → `TIERED_SLAB`; backfill `rateType:"FIXED"` in tier JSONB; replace `chk_tiered_requires_tiers` with per-type constraints |

## Testing

| Layer | Scope | Tooling |
|-------|-------|---------|
| Domain unit | `TierFormulaEvaluator` — all four formula types, JPY scaling, bracketAmount | JUnit 5 + AssertJ (zero Spring) |
| Application unit | `FeeSessionRunner` — dual globals, deduplication, STEP accumulation (real KieContainer) | JUnit 5 + real KIE session |
| Application unit | `CalculateFeesService`, `ManageFeeRulesService`, `DryRunFeeCalculationService` — mocked repository | Mockito |
| Persistence slice | `FeeRuleRepositoryAdapter` — account override, tier JSONB round-trip, constraint enforcement | `@DataJpaTest` + Testcontainers PostgreSQL |
| REST slice — calculation | `FeeCalculationController` — validation, error handling | `@WebMvcTest` |
| REST slice — admin | `FeeRuleAdminController`, `DryRunFeeCalculationController` — auth (scopes), CRUD, 404/409 | `@WebMvcTest` + `@Import(SecurityConfig.class)`, `jwt().authorities(...)` |
| Validator unit | `FeeRuleRequestValidator` — per-fee-type and per-rateType field rules | Zero Spring context |
| Drools unit | Flat, percentage, tiered slab, tiered step, free — all formula types, bearers, boundary cases | `DroolsTestSupport` (delegates to `FeeSessionRunner`) + real KIE session |
| Integration | TIERED_STEP via admin+calculation endpoints, TIERED_SLAB hybrid, dry-run | `@SpringBootTest` + MockMvc + Testcontainers |

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
