# genc_hackathon — Insurance Portal

A customer + agent insurance portal (Spring Boot) where **every user action
writes a structured JSON log line** in real time. Those logs are the input for
a separate log-monitoring project — the whole point of this app is to generate
realistic, correlated activity logs you can play with.

Each person runs it against **their own free Supabase (Postgres) database**, so
no credentials are shared and everyone's data/logs stay their own.

> 📁 **The app lives in the [`insurance-portal/`](insurance-portal/) subfolder**
> of this repo. All commands below are run from there. A more detailed
> developer README is at [`insurance-portal/README.md`](insurance-portal/README.md).

---

## What your friends need to do (start to finish)

### 1. Prerequisites (install once)
- **JDK 17 or newer** (a full JDK, not just a JRE)
- **Maven 3.8+** (or an IDE with Java + Spring Boot support — VS Code
  "Extension Pack for Java", or IntelliJ)

### 2. Clone the repo
```bash
git clone https://github.com/ParveenSultana16/genc_hackathon.git
cd genc_hackathon/insurance-portal
```
> Note the **two** `insurance-portal` levels aren't a typo — the repo folder
> contains the project folder. You want the one that has `pom.xml` in it.

### 3. Create your own free Supabase project
1. Sign up at **https://supabase.com** and create a new project
   (an **empty** project is fine — this app creates its own tables on first run).
2. Set a **database password** when creating it, and remember it
   (you can reset it any time under *Project Settings → Database*).

### 4. Add your Supabase connection details
From the `insurance-portal/` folder, copy the template:
```bash
cp .env.example .env
```
Then open `.env` and fill in **your own** project's three values:
```properties
SUPABASE_DB_URL=jdbc:postgresql://aws-0-<your-region>.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres.<your-project-ref>
SUPABASE_DB_PASSWORD=<your-database-password>
```
Where to find these: **Supabase Dashboard → your project → Project Settings →
Database → "Connection string" → JDBC tab**, and use the **Connection pooler**
host/user (it works over IPv4). The username format is `postgres.<project-ref>`.

> 🔒 `.env` is git-ignored — it holds your password and must never be committed.
> The comments inside `.env.example` walk through each value.

### 5. Run it
```bash
mvn spring-boot:run
```
(Or open the `insurance-portal/` folder in your IDE and run
`InsurancePortalApplication`.) Then open **http://localhost:8080**.

First startup takes a little longer — Maven downloads dependencies, Hibernate
creates the tables in your Supabase project, and demo accounts are seeded.

### 6. Log in and use it
Demo accounts are created automatically on first run:

| Role | Username / Email | Password |
|---|---|---|
| Customer (has a policy + claim) | `jane.doe` | `password123` |
| Customer (fresh account) | `new.customer` | `password123` |
| Agent | `agent@company.com` | `agent123` |

You can also register a new customer from the login page.

### 7. Watch the logs (the whole point)
Every action appends one JSON line to `logs/app.log` (inside the
`insurance-portal/` folder) in real time:
```bash
# Git Bash / macOS / Linux
tail -f logs/app.log
# Windows PowerShell
Get-Content logs\app.log -Wait
```

Stop the app with **Ctrl+C**.

---

## Troubleshooting

| Message in the terminal | Cause | Fix |
|---|---|---|
| `Could not resolve placeholder 'SUPABASE_DB_URL'` | `.env` not found | Make sure you ran `mvn` from the `insurance-portal/` folder (the one with `.env` + `pom.xml`) |
| `FATAL: password authentication failed` | Wrong DB password | Fix `SUPABASE_DB_PASSWORD` in `.env` |
| `there is no POM in this directory` | Wrong folder | `cd` into `insurance-portal/` first |
| `Web server failed to start. Port 8080 was already in use` | An earlier run is still open | Ctrl+C the old one, or close that terminal, then re-run |
| Yellow `WARN` lines (`HHH90000025`, `open-in-view`) | Harmless notices, not errors | Ignore them |

---

## How it works (short version)
- **Spring Boot + Thymeleaf**, session-based auth, JPA/Hibernate over Postgres.
- Customer features: policies, claims, premium payments, document downloads.
- Agent features: customer/policy/claim management, payments, reports.
- Every service method calls a `LogWriter` that emits one correlated JSON log
  line per action. A shared `correlation_id` ties one customer's whole journey
  together across login → policy → claim → payment.

See [`insurance-portal/README.md`](insurance-portal/README.md) for the full log
schema, event-type reference, and design decisions.
