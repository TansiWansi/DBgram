# Deployment

DBgram's default runtime is a Spring Boot API, PostgreSQL, persistent media storage, and an Expo web export behind Caddy HTTPS. The repository also retains the original distributed-service infrastructure for further integration work. Use the root `compose.yml` for the portable runtime.

## Render (no server required)

Use the root `render.yaml` Blueprint to create one Docker Web Service, managed PostgreSQL and a persistent media disk. [Exact account setup and credentials](RENDER.md) are documented separately. The hosted image serves the frontend and API from the same HTTPS origin. Render handles TLS and supplies the listening port and generated database credentials.

## Existing Linux server

Prerequisites: Docker Engine with Compose v2, a domain whose DNS points to the server, inbound TCP 80/443 and UDP 443, and sufficient disk space for media/database backups. A starting server size of 2 CPU cores and 4 GB RAM is an estimate, not a validated capacity guarantee. Video processing increases memory/CPU demand.

1. Copy this project to the server, excluding `.runtime`, `.env`, build output and `node_modules`.
2. Copy `.env.example` to `.env`. Set `SITE_ADDRESS`, `APP_ORIGIN`, `ACME_EMAIL`, a random database password and a random JWT secret. Generate the latter with `openssl rand -base64 64`. Use a single line.
3. Run `docker compose build` and then `docker compose up -d` from the repository root.
4. Run `docker compose ps` and `docker compose logs --tail=100 api`. Check `https://YOUR_DOMAIN/actuator/health` returns UP.
5. Register two accounts in independent browser profiles, publish a public post, and verify likes/comments. Create a private post and confirm the other account cannot read it. Run `DBGRAM_TEST_URL=https://YOUR_DOMAIN node scripts/smoke.mjs` only on a disposable/staging database: it creates test users/content.

By default, only Caddy publishes ports. The optional Adminer tools profile publishes a loopback-only port for local database administration. PostgreSQL and API ports stay inside the Docker network. Caddy obtains and renews HTTPS certificates. Browser API requests use the same origin and HttpOnly refresh cookies. `COOKIE_SECURE` must remain true on public hosts.

The API's `/app/uploads` and PostgreSQL volumes are durable across container replacements. `docker compose down -v` deletes persistent data; do not use it to update the application.

## Update and rollback

Back up PostgreSQL and media before upgrading. Build/test the new source, then use `docker compose up -d --build`. Flyway applies migrations at startup. Retain the previous images and a matching database backup: reverting an image does not undo a schema migration. Never remove a Flyway migration that has already run.

Back up the database using `docker compose exec -T postgres pg_dump -U dbgram -d dbgram -Fc > dbgram.dump` in a binary-safe shell. Back up `media_data` separately and store backups outside this server. Periodically test restoration into a separate environment.

## Optional integrations

Stripe requires `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, and the configured subscription price. Configure Stripe's endpoint as `https://YOUR_DOMAIN/api/webhooks/stripe`. Test checkout, webhook retry, cancellation and expiry before enabling paid access for real customers. Never grant access from a checkout redirect.

`DBGRAM_ADMIN_USER_IDS` explicitly lists the UUIDs of trusted moderation operators. Keep this list under deployment administration; ordinary users must never be able to self-assign moderation privileges.

FFmpeg is included in the API image. Media processing is enabled in Compose and writes variants to the persistent media volume. Processing status must reach READY before variants are usable.

Google Workspace/Meet and an L2 blockchain network/wallet need separate account configuration and implementation verification. A URL or a SHA-256/Merkle proof is not evidence of a created Meet meeting or a blockchain anchor.

## Development

Use Java 21, PostgreSQL 16+, and Node 24. Set `JWT_SECRET`, `DATABASE_URL` (JDBC form with separate credentials, or a PostgreSQL URI containing provider-generated credentials), `DATABASE_USER`, `DATABASE_PASSWORD`, and `COOKIE_SECURE=false` for loopback HTTP only. Run `mvn test` and `mvn spring-boot:run` in `backend`. Run the frontend lint/typecheck/export commands, then `node scripts/serve-web.mjs` to preview the static export with an API proxy at `http://127.0.0.1:8787`.

## Local PostgreSQL administration with Adminer

The root `compose.yml` includes the optional `adminer` service using the official `adminer:6.1.1-standalone` image. It joins the database network, defaults to the hostname `postgres`, and waits for PostgreSQL to be healthy. It runs only when explicitly selected or when the `tools` profile is enabled. The host port is bound to `127.0.0.1`.

From the repository root, create `.env` from `.env.example` if you have not already configured Compose, and set `POSTGRES_PASSWORD`. Keep the other existing settings populated: Compose evaluates their required environment variables even when only starting selected services. Then run:

```powershell
docker compose --profile tools up -d postgres adminer
```

Open **http://localhost:8082** and log in with:

| Field | Value |
| --- | --- |
| System | PostgreSQL |
| Server | `postgres` |
| Username | `dbgram` |
| Password | Your existing `POSTGRES_PASSWORD` from `.env` |
| Database | `dbgram` |

Adminer uses PostgreSQL's internal port 5432. Set `ADMINER_PORT` in `.env` to change only the local browser port. Use the existing database password if the PostgreSQL volume was already initialized; changing `.env` does not change an existing PostgreSQL role's password. `ADMINER_DEFAULT_SERVER` selects the host; enter the system, database and credentials on the login screen. See the [official image documentation](https://hub.docker.com/_/adminer).

This service connects to the **Compose `postgres` database**. The Windows launcher `scripts/start-local.ps1` starts a separate PostgreSQL instance under `.runtime`; its data is not automatically transferred to the Docker volume. The application creates the schema through Flyway when its API starts against the Compose database.

To add Adminer to the full Compose environment, run `docker compose --profile tools up -d`. To stop just the database UI, run `docker compose stop adminer`; the PostgreSQL data volume is preserved. This optional service is not part of the Render blueprint.

## Deployment state

A generated Docker configuration does not mean a public server is deployed. Deployment is complete only after the target hosting account/server is known, credentials are available through the host's normal authentication mechanism, and the live HTTPS health check and multi-user smoke tests pass.


## API addresses and domains

No application code requires a LAN IP. The web export defaults to `/api`, and Caddy forwards those requests to the `api` Docker service by DNS name. Media URLs and resumable uploads follow that origin. Moving the server requires updating DNS, `SITE_ADDRESS` and `APP_ORIGIN`; the web bundle needs no address change or rebuild. Keep the domain in `APP_ORIGIN` consistent with the public URL.

Native clients require `EXPO_PUBLIC_API_URL=https://YOUR_DOMAIN/api` in the frontend environment before starting Expo or producing a build. This public variable contains only an address, never secrets. A missing native URL produces an explicit configuration error instead of contacting an old network address. For Expo web served directly by Metro, set `EXPO_PUBLIC_API_URL=http://localhost:8080/api`, or preview the exported app through `scripts/serve-web.mjs` with its same-origin proxy.

The API listener uses `SERVER_ADDRESS` (default `0.0.0.0`) and `PORT` (default `8080`). `DATABASE_URL` uses a database hostname or Docker service name. `CORS_ORIGINS` accepts a comma-separated list of explicit allowed frontend origins; it does not allow arbitrary origins with credentials. `DBGRAM_PUBLIC_URL` supplies the public origin used by payment redirects; on Render it defaults to the assigned `RENDER_EXTERNAL_URL`. Checkout is unavailable until that origin and payment credentials are configured.

The local preview accepts `WEB_HOST`, `WEB_PORT` and `BACKEND_URL`. The local launcher accepts `-ApiPort`, `-WebPort`, `-ApiBindAddress` and `-AppOrigin`; for example, `./scripts/start-local.ps1 -ApiPort 8090 -WebPort 8790` with `BACKEND_URL=http://localhost:8090` and `WEB_PORT=8790` on the preview process. Loopback defaults serve local development only.
