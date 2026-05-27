# infraforge — Claude Code Instructions

## Project Overview

Mono-repo: AI-powered Internal Developer Platform. Three components:

| Directory        | Stack                                   |
| ---------------- | --------------------------------------- |
| `control-plane/` | Java 25 · Spring Boot 3.5 · Gradle 8.12 |
| `agent/`         | Python 3.13 · LangGraph 0.3 · FastAPI   |
| `ui/`            | Next.js 15 · React 19 · TypeScript 5    |

See `PLAN.md` for phase-by-phase status. See `docs/architecture.md` for system design.

---

## Mandatory Workflow — Follow This For Every Task

### 1. Write tests alongside code

Every non-trivial change must include tests. Do not write code and skip tests with "I'll add tests later."

### 2. Run the affected test suite

After completing any change, run the tests for every component you touched.

**control-plane** — requires the Gradle wrapper. Generate it once if missing:

```bash
# Only needed once — run from repo root if gradlew is absent:
cd control-plane && gradle wrapper --gradle-version 8.12
# Then run tests:
cd control-plane && ./gradlew test
```

**agent**:

```bash
cd agent && uv run pytest
```

**ui** — type-check is the primary gate (Jest tests added in Phase 4):

```bash
cd ui && npm run type-check
# When Jest tests exist:
cd ui && npm test -- --passWithNoTests
```

### 3. Fix all failures before proceeding

If any test fails, diagnose and fix it. Do not mark a task complete while tests are red. Fix root cause — not symptoms.

### 4. Self-review all changed files

After tests pass, re-read every file you modified and check:

- [ ] The code correctly implements what was asked, including edge cases
- [ ] Every new code path has at least one test
- [ ] No hardcoded secrets, tokens, or environment-specific values in source
- [ ] Input validation exists at system boundaries (controllers, API clients) — not inside domain objects
- [ ] Error paths are handled: adapters log and translate exceptions; domain code never swallows errors silently
- [ ] No security issues: no command injection, SQL injection, or unvalidated external input reaching sensitive operations
- [ ] Naming, formatting, and structure are consistent with the surrounding file — don't introduce a new style
- [ ] No unused imports, dead code, or commented-out blocks left behind
- [ ] No speculative abstractions — solve the actual problem, not a hypothetical future one

### 5. Fix any review findings, then re-run tests

If review surfaces anything, fix it and run tests again to confirm nothing broke.

---

## Code Conventions

### Java (control-plane)

- **Domain is pure.** `domain/` and `ports/` have zero AWS SDK or Spring framework imports.
- **Adapters self-register.** Use `@Component @Profile({"aws","local"})` on AWS adapters, `@Component @Profile("test")` on local adapters. No separate wiring config files.
- **Immutable records.** `InfraRequest` and other domain objects are records. State transitions return new instances — never mutate.
- **Sealed state.** `RequestState` is a sealed interface. Use pattern-matching switch (`switch (state) { case Submitted s -> ... }`) — the compiler enforces completeness.
- **Tests use the `test` profile.** Annotate Spring integration tests with `@ActiveProfiles("test")`. The in-memory adapters wire automatically.
- **`@Value` in constructors.** When an adapter needs a config value, inject via `@Value("${...}")` on the constructor parameter — not via `@Autowired` field injection.

### Python (agent)

- Type annotations on all public functions and `TypedDict` fields — `mypy --strict` must pass.
- `async` for all tool calls (use `httpx.AsyncClient`).
- LangGraph node functions accept `AgentState` and return a `dict` of partial state updates — never mutate the input dict.
- `target_cloud` must be passed through to the `submit_node` and included in the `POST /internal/requests` payload.

### TypeScript (ui)

- Server Components for static data fetching; Client Components only when hooks or interactivity are required.
- All backend calls go through typed clients in `src/lib/api/` — no inline `fetch()` in components or pages.
- No `any` types.
- The Next.js rewrites in `next.config.ts` proxy `/cp/**` → Control Plane and `/agent/**` → Agent. Never hardcode backend URLs in components.

---

## Key Design Decisions

- **Ports & adapters exist for testability, not cloud portability.** infraforge itself always runs on AWS. The ports allow tests to run without AWS credentials using in-memory adapters.
- **Multi-cloud means target infra, not infraforge's own infra.** `CloudProvider` on `InfraRequest` (AWS/GCP/AZURE) controls where the user's generated Terraform is applied. infraforge's own DynamoDB, SQS, etc. stay on AWS.
- **`infra-modules/` is organised by provider.** `infra-modules/aws/`, `infra-modules/gcp/`, `infra-modules/azure/`. The agent's `context_fetch_node` filters modules by `target_cloud`.
- **Three security boundaries.** `/internal/**` = service key (agent only), `/api/**` = JWT Bearer (UI), `/auth/**` = GitHub OAuth. These are enforced by separate `SecurityFilterChain` beans ordered 1–3.
