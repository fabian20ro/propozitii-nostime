# Operations Runbook

> Source of truth: `render.yaml`, `Dockerfile`, `.github/workflows/`

## Architecture

```
GitHub Pages (frontend) --> Render (backend, GraalVM native) --> Supabase (PostgreSQL)
```

All three services run on free tiers.

## Deployment

### Backend (Render)

- **Trigger**: Push to `master` auto-deploys via `render.yaml`
- **Build**: Docker multi-stage -- GraalVM native compilation (~5 min), then minimal runtime image (~50 MB)
- **Health check**: `GET /q/health` (Render polls this)
- **Environment**: `PORT`, `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD` set in Render dashboard (`sync: false`)

### Frontend (GitHub Pages)

- **Trigger**: Push to `master` changing `frontend/**`, or manual workflow dispatch
- **Pipeline**: Upload `frontend/` directory as Pages artifact, deploy

### Database (Supabase)

- Schema managed by Flyway (runs at backend startup via `quarkus.flyway.migrate-at-start=true`)
- `baseline-on-migrate=true` handles the existing database without re-running V1
- Dictionary data loaded once via `./gradlew loadDictionary`

## Monitoring

### Health Endpoint

```bash
curl https://propozitii-nostime.onrender.com/q/health
```

Returns HTTP 200 with `{"status": "UP"}` when healthy.

### Logs

- **Render dashboard**: View real-time logs at render.com
- **Log level**: `INFO` (default), `DEBUG` for `scrabble.phrases` package

## Common Issues

### Render Cold Starts

**Symptom**: First request after inactivity takes 30-60 seconds.
**Cause**: Render free tier spins down after ~15 min of inactivity.
**Mitigation**: GraalVM native image starts in <1 second. Frontend polls `/q/health` for up to 60 seconds.
**Note**: This is inherent to the free tier and cannot be eliminated.

### Database Connection Failures

**Symptom**: HTTP 500 with `PSQLException: Connection refused`.
**Cause**: Supabase credentials missing/wrong, or Supabase project paused (free tier pauses after 7 days of inactivity).
**Fix**:
1. Check env vars in Render dashboard
2. Verify Supabase project is active at supabase.com
3. If paused, resume the project in Supabase dashboard

### Flyway Migration Failures

**Symptom**: Backend fails to start with `FlywayValidateException`.
**Cause**: Schema was modified outside Flyway, or migration file was changed after being applied.
**Fix**:
1. Never modify already-applied migration files
2. If schema was manually changed, repair with: connect to Supabase and run `DELETE FROM flyway_schema_history WHERE success = false`
3. Create a new migration file for schema changes (e.g., `V2__add_column.sql`)

### Testcontainers Failures in CI

**Symptom**: `Could not find a valid Docker environment` in CI.
**Cause**: Should not happen on GitHub Actions (Docker is pre-installed). If it does, check the runner image.

### Connection Pool Exhaustion

**Symptom**: Requests timeout with `AgroalException`.
**Cause**: Pool `max-size=2` exhausted under concurrent load.
**Fix**: Increase `quarkus.datasource.jdbc.max-size` in `application.properties` (check Supabase connection limits first).

## Rollback Procedures

### Backend Rollback

1. **Via Render**: Go to Render dashboard > Deploys > click "Rollback" on a previous successful deploy
2. **Via git**: Revert the commit and push to `master`
   ```bash
   git revert HEAD
   git push origin master
   ```

### Frontend Rollback

1. Revert the commit touching `frontend/` and push
2. Or re-run the frontend workflow on a previous commit via GitHub Actions

### Database Rollback

Flyway is forward-only (no undo migrations). For emergencies:

1. Connect to Supabase SQL editor
2. Manually reverse the migration (e.g., `ALTER TABLE words DROP COLUMN ...`)
3. Delete the corresponding row from `flyway_schema_history`
4. Deploy the reverted backend code

For a full reset:
```sql
DROP TABLE IF EXISTS words;
DROP TABLE IF EXISTS flyway_schema_history;
```
Then restart the backend (Flyway recreates everything) and run `./gradlew loadDictionary`.

## Operational Parameters

| Parameter | Value | Location |
|-----------|-------|----------|
| Connection pool max | 2 | `application.properties` |
| Connection pool idle timeout | 5 min | `application.properties` |
| HTTP port | `${PORT:8080}` | `application.properties` |
| Flyway migrate at start | true | `application.properties` |
| CORS allowed origin | `fabian20ro.github.io` | `application.properties` |
| Docker health check interval | 30s | `Dockerfile` |
| Docker health check retries | 3 | `Dockerfile` |
