# Insurance Portal (Hackathon Prototype)

A working customer + agent insurance portal prototype. Every action a user
takes generates a structured JSON log line, so a separate monitoring
application can trace a customer's full journey - login, policy, claim,
and payment - end to end using a shared `correlation_id`.

This is deliberately built as a **fast, working prototype**, not a
production system: one monolithic Spring Boot app, session-based auth, and
an embedded file-based database. See [Design Decisions](#design-decisions)
below for why, if you're curious.

---

## Quick start

### Requirements
- Java 17 or newer (JDK, not just a JRE)
- Maven 3.8+ (or just use your IDE's built-in Maven support)
- A free [Supabase](https://supabase.com) project (this app stores its data in
  Postgres). You use **your own** project - see the one-time setup below.

### One-time database setup

The app connects to **your own** Supabase project, so your data and logs are
yours. Nobody shares a database.

1. Create a free project at https://supabase.com (an empty project is fine -
   the app creates all its tables automatically on first run).
2. Copy the example env file and fill in your project's connection details:
   ```bash
   cp .env.example .env
   ```
   Then edit `.env` and set `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, and
   `SUPABASE_DB_PASSWORD` - `.env.example` explains exactly where each value
   comes from in the Supabase dashboard. `.env` is gitignored and never
   committed.

That's it - `application.properties` auto-loads `.env` on startup, so you don't
need to export any shell variables.

### Run it

**From VS Code:**
1. Open this folder in VS Code (`File > Open Folder`)
2. Make sure you have the **Extension Pack for Java** and **Spring Boot
   Extension Pack** installed (VS Code will usually prompt you to install
   these when it detects a Maven/Spring Boot project)
3. Open `src/main/java/com/hackathon/insuranceportal/InsurancePortalApplication.java`
4. Click the **Run** button above the `main` method (or press `F5`)

**From the command line:**
```bash
mvn spring-boot:run
```

Either way, once it's running, open **http://localhost:8080** in your browser.
Every action you take appends a JSON log line to `logs/app.log` in real time -
`tail -f logs/app.log` to watch it live.

> **Sharing this app?** Everyone who clones it just does the one-time database
> setup above with their *own* Supabase project. The code carries no secrets,
> and each person's actions/logs go to their own database + their own local
> `logs/app.log`.

### Demo credentials

The app seeds these automatically the first time it starts (see `DataSeeder.java`):

| Role | Username / Email | Password | Notes |
|---|---|---|---|
| Customer | `jane.doe` | `password123` | Already has a policy and a claim - good for demoing tracking/payments immediately |
| Customer | `new.customer` | `password123` | Fresh account, no policy yet - good for demoing the "no correlation_id until first policy" behavior |
| Agent | `agent@company.com` | `agent123` | Full agent access |

You can also register a brand-new customer account from the login page.

---

## What this app does (matches your flowchart exactly)

**Customer flow:** Login &rarr; Customer Home &rarr; one of:
1. **My Policies** - view policies, buy a new one
2. **Claim Management** - raise a claim, track a claim by ID
3. **Premium Payments** - pay a premium (simulated gateway), view payment history
4. **Document Downloads** - download a policy certificate

**Agent flow:** Login &rarr; Agent Home &rarr; one of:
1. **Customer Management** - view/search all customers
2. **Policy Management** - create a policy for a customer, approve a pending policy, renew a policy
3. **Premium Payment** - record a manual payment on a customer's behalf
4. **Claim Review** - review, approve, reject, or settle a claim
5. **Reports** - summary counts across customers, policies, claims, payments

Every one of the actions above writes exactly one structured log line -
see [Log Schema](#log-schema) below.

---

## Log schema

Every action writes one JSON line to `logs/app.log`. The common envelope
(present on every line) plus feature-specific extra fields:

```json
{
  "timestamp": "2026-08-23T10:15:41Z",
  "application_name": "PolicyService",
  "server_id": "policy-app-01",
  "environment": "PROTOTYPE",
  "correlation_id": "CORR-2026-000101",
  "customer_id": "CUST-101",
  "policy_id": "POL-2026-1000102",
  "claim_id": null,
  "event_type": "Policy Created",
  "stage": "POLICY_CREATION",
  "status": "SUCCESS",
  "duration_ms": 1875,
  "error": null,
  "proposal_id": "PROP-2026-1000103",
  "policy_type": "HEALTH",
  "agent_id": null
}
```

### The most important design decision: `correlation_id`

`correlation_id` is **not** generated at login. It is generated exactly
once, the first time a policy is created for a customer, and stored on
that customer's record. Every log line for that customer - past, present,
and future, across every feature - reuses that same `correlation_id`.

This means:
- A brand-new customer with no policy yet will show `correlation_id: null`
  in their login logs. That's correct, not a bug - they have no traceable
  history yet.
- The moment their first policy is created (self-service or by an agent),
  a `correlation_id` is generated and attached to that customer going
  forward.
- A downstream monitoring app can filter the whole log file by one
  `correlation_id` and reconstruct a single customer's entire relationship
  with the company - every login, every claim, every payment, in order.

See `LogWriter.java` and `PolicyService.createPolicy()` for exactly where
this happens in code.

### Where the logging code lives
- `logging/LogEvent.java` - a fluent builder for one log line
- `logging/LogWriter.java` - looks up `correlation_id`, serializes to
  JSON, appends to `logs/app.log`. **Never throws** - a logging failure
  will never break a real user action.
- Every `service/*.java` class calls `logWriter.write(...)` at the point
  where an action succeeds or fails - that's the single source of truth
  for what gets logged. Controllers never log directly.

### event_type / stage reference

| Feature | event_type (success) | event_type (failure) | stage | status values |
|---|---|---|---|---|
| Login | `LOGIN` | `LOGIN` | `LOGIN` | SUCCESS / FAILED |
| My Policies | `Policy Viewed` | - | `POLICY_LOOKUP` | SUCCESS |
| Buy/Create Policy | `Policy Created` | - | `POLICY_CREATION` | SUCCESS |
| Approve Policy | `Policy Approved` | - | `POLICY_APPROVAL` | SUCCESS |
| Renew Policy | `Policy Renewed` | - | `RENEWAL_PROCESSING` | SUCCESS |
| Submit Claim | `Claim Submitted` | - | `CLAIM_SUBMISSION` | SUCCESS |
| Track Claim | `CLAIM_TRACKING` | `CLAIM_TRACKING` | `CLAIM_TRACKING` | SUCCESS / FAILED |
| Review/Approve/Reject/Settle Claim | `Claim Reviewed` / `Claim Approved` / `Claim Rejected` / `Claim Settled` | - | `CLAIM_REVIEW` / `CLAIM_APPROVAL` / `CLAIM_REJECTION` / `CLAIM_SETTLEMENT` | SUCCESS |
| Pay Premium (customer) | `Payment Successful` | `Payment Failed` | `PREMIUM_COLLECTION` | SUCCESS / FAILED |
| Record Payment (agent) | `Payment Recorded` | - | `PREMIUM_COLLECTION` | SUCCESS |
| Customer Lookup (agent) | `Customer Looked Up` | `Customer Looked Up` | `CUSTOMER_LOOKUP` | SUCCESS / FAILED |
| Reports | `Report Generated` | - | `REPORT_GENERATION` | SUCCESS |
| Document Download | `Document Downloaded` | - | `DOCUMENT_DOWNLOAD` | SUCCESS |

### Where to find the log file
`logs/app.log`, relative to wherever the app was started from. It's
plain JSON Lines (one JSON object per line) - trivially readable by any
log viewer, `tail -f`, or a monitoring app that just polls/tails the file.

---

## Simulated failures (on purpose)

To demo the failure-path logging without needing real integrations:
- **Login**: fails if the username/password doesn't match a real account
- **Premium payment (customer self-service only)**: ~12% of attempts are
  randomly declined (`PaymentService.FAILURE_RATE_PERCENT`), simulating a
  real gateway decline. Agent-recorded manual payments always succeed
  (they're not going through a gateway).
- **Claim tracking**: fails if you enter a claim ID that doesn't exist
- **Customer search**: "fails" (logged as FAILED) if no customers match
  the search query

---

## Project structure

```
insurance-portal/
├── pom.xml
├── README.md
├── src/main/java/com/hackathon/insuranceportal/
│   ├── InsurancePortalApplication.java   Entry point
│   ├── config/
│   │   ├── WebConfig.java                Registers session-auth interceptors
│   │   ├── SessionAuthInterceptor.java   Redirects to login if not authenticated
│   │   └── DataSeeder.java               Seeds demo accounts on first run
│   ├── entity/                           JPA entities: Customer, Agent, Policy, Claim, Payment
│   ├── repository/                       Spring Data JPA repositories
│   ├── service/                          Business logic + ALL logging calls live here
│   │   ├── AuthService.java
│   │   ├── PolicyService.java
│   │   ├── ClaimService.java
│   │   ├── PaymentService.java
│   │   ├── CustomerLookupService.java
│   │   ├── ReportService.java
│   │   └── DocumentService.java
│   ├── controller/                       Thin web layer - no business logic, no logging
│   │   ├── HomeController.java
│   │   ├── CustomerAuthController.java
│   │   ├── AgentAuthController.java
│   │   ├── CustomerController.java
│   │   ├── AgentController.java
│   │   └── GlobalExceptionHandler.java
│   ├── logging/
│   │   ├── LogEvent.java                 Fluent builder for one log line
│   │   └── LogWriter.java                Writes JSON lines, resolves correlation_id
│   ├── exception/                        Custom exceptions (carry a reason code for logs)
│   └── util/
│       └── IdGenerator.java              Generates POL-/CLM-/CORR-/PAY- style IDs
├── src/main/resources/
│   ├── application.properties
│   ├── templates/                        Thymeleaf views (server-rendered HTML)
│   │   ├── index.html, customer-login.html, agent-login.html, ...
│   │   ├── customer/                     My Policies, Claim Management, Payments, Documents
│   │   └── agent/                        Customer Mgmt, Policy Mgmt, Payment, Claim Review, Reports
│   └── static/css/style.css
└── .env.example                           Copy to .env and add your Supabase creds
```

---

## Design decisions

These were deliberate choices for a **hackathon prototype**, not
oversights - see the reasoning if you're deciding whether to change them:

| Decision | Why |
|---|---|
| **One monolithic app**, not 4 microservices | Four services means a gateway, inter-service HTTP calls, and correlation-ID propagation across network boundaries - all setup cost with no demo-visible payoff at this stage. |
| **Supabase (hosted Postgres)**, not a local embedded DB | Real managed Postgres with zero local install, persists across restarts, and lets each person run the app against their own free cloud database by just filling in a `.env`. (Earlier iterations used embedded H2; H2 is now kept only as a test-scope dependency.) |
| **Session-based auth**, not Spring Security/JWT | A plain `HttpSession` + a lightweight interceptor is enough to demo role-based access convincingly, without the configuration overhead of Spring Security. |
| **Explicit `LogWriter.write(...)` calls**, not AOP/aspect-based auto-logging | Easier to read, debug, and modify under time pressure - you can see exactly what gets logged and why by reading the service method, instead of tracing through aspect weaving. |
| **`correlation_id` generated at first policy creation**, not at login | Matches the reference log data: it traces a customer's entire relationship with the company, not a single browser session. |

If you later move this toward production, the natural next steps (in
order) would be: Spring Security + JWT, split into real microservices,
swap H2 for MySQL/Postgres on RDS, and ship logs via Filebeat/Kafka
instead of a shared file.

---

## Feeding the monitoring app

Since this all runs as one process, `logs/app.log` is the single
integration point. A separate monitoring application can:
- Tail the file in real time (`tail -f logs/app.log`) for a live demo
- Poll and parse it periodically
- Group lines by `correlation_id` to reconstruct one customer's full
  journey across login, policy, claim, and payment events - including
  exactly where something failed (`status: FAILED` + the `error` field)

No additional wiring is needed on this app's side - every action already
writes a complete, correctly-correlated log line the moment it happens.
