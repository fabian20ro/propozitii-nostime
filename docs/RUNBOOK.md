# Operations Runbook

Source of truth: `render.yaml`, `Dockerfile`, `src/main/resources/application.properties`

## Architecture

```text
GitHub Pages (frontend) -> Render.com (backend, Quarkus JVM uber-jar) -> Supabase (PostgreSQL)
```

## Deployment

### Backend (Render)

- Trigger: push to `master`.
- Runtime: Docker image based on Temurin 21 JRE.
- Build artifact: Quarkus uber-jar (`*-runner.jar`), tests skipped in Docker build stage.
- Health check: `GET /q/health`.
- Required env vars in Render dashboard:
- `SUPABASE_DB_URL`
- `SUPABASE_DB_USER`
- `SUPABASE_DB_PASSWORD`

### Frontend (GitHub Pages)

- Trigger: push to `master` with changes under `frontend/**`, or manual workflow dispatch.
- Workflow: `.github/workflows/frontend.yml`

### Database (Supabase)

- Schema migrations via Flyway files in `src/main/resources/db/migration/`.
- In production, auto-migrate at startup is disabled (`%prod.quarkus.flyway.migrate-at-start=false`).
- Apply production schema changes manually (or with controlled one-off migration job).
- Dictionary load is one-time per new DB with `./gradlew loadDictionary`.

## Monitoring

### Health Check

```bash
curl https://propozitii-nostime.onrender.com/q/health
```

Expected healthy response:
```json
{"status":"UP"}
```

### Logs

- Backend logs: Render dashboard.
- Default log level: `INFO`.
- Package debug level: `scrabble.phrases` set to `DEBUG`.

## Common Incidents

### Cold Start Latency

Symptom:
- first request after idle can be slow on free tier.

Mitigation:
- frontend polls `/q/health` for up to 60s before retrying sentence fetch.

### Database Connection Errors

Symptom:
- `PSQLException`, backend 500s.

Checks:
1. Verify Render env vars are set correctly.
2. Verify Supabase project is active.
3. Validate connectivity from backend logs.

### Migration Drift / Validation Failures

Symptom:
- Flyway validation errors in non-prod environments.

Fix:
1. Do not modify already-applied migration files.
2. Add new migration files for schema changes.
3. If needed, repair failed Flyway history entries carefully.

### Rate Limiting Complaints

Behavior:
- backend limits to 30 requests/minute per `X-Forwarded-For` first IP.
- returns HTTP 429 with JSON error body.

## Rollback

### Backend

1. Render dashboard rollback to previous deploy.
2. Or `git revert <commit>` and push to `master`.

### Frontend

1. Revert commit touching `frontend/**` and push.
2. Or redeploy older commit via GitHub Actions run.

### Database

- Flyway is forward-only by default.
- Emergency rollback is manual SQL and should be documented per incident.

## Operational Parameters

| Parameter | Value | Source |
|---|---|---|
| Rate limit | 30 req/min/IP | `RateLimitFilter.kt` |
| DB pool max | 4 | `application.properties` |
| DB acquisition timeout | 60s | `application.properties` |
| Prod flyway auto-migrate | disabled | `application.properties` |
| Health path | `/q/health` | Quarkus config |
