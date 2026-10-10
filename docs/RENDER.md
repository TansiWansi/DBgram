# Deploy DBgram to Render

Use **New → Blueprint**, linked to the existing GitHub repository `https://github.com/TansiWansi/DBgram`. The chosen branch must contain the updated root `render.yaml` and Dockerfile. The local changes need to be pushed before Render can build them. Keep the service root at the repository root.

Create a project named **DBgram** and select its **Production** environment when Render asks where to place the Blueprint. The Blueprint provisions the following resources together in **Singapore**:

| Resource | Configuration |
| --- | --- |
| Web Service `dbgram` | Docker; 1 CPU / 2 GB RAM (`1c-2g`); one instance |
| PostgreSQL `dbgram-postgres` | PostgreSQL 16; 0.5 CPU / 1 GB RAM (`0.5c-1g`); 15 GB storage |
| Media disk | 10 GB; mounted at `/app/uploads` |

These are paid resources. Review the cost shown by Render before creating them. The sizes are initial estimates, not a measured traffic capacity. The disk makes the service a single-instance deployment; horizontal scaling requires moving media into shared object storage first. Render documents the [Blueprint fields](https://render.com/docs/blueprint-spec) and [disk constraints](https://render.com/docs/disks).

## Database credentials

The database name is **`dbgram`**, and the username is **`dbgram`**. Render generates the password and private hostname. Port **5432** is PostgreSQL's default. The Blueprint injects the complete **internal connection URI** into the Web Service's `DATABASE_URL` using `fromDatabase.connectionString`.

**No password needs to be pasted into chat or committed to Git.** The backend accepts `postgres://user:password@hostname:port/database` and `postgresql://...`, converts them to JDBC configuration, and keeps credentials out of the JDBC URL. Existing `jdbc:postgresql://...` URLs still work with separate `DATABASE_USER` and `DATABASE_PASSWORD` variables. `DATABASE_SSL_MODE=require` enables TLS for Render's internal database connection. External database access is disabled by the Blueprint. [Render's connection documentation](https://render.com/docs/postgresql-creating-connecting) explains internal URLs and supported TLS modes.

Render also generates `JWT_SECRET`. Keep this secret stable across redeploys. The service uses Render's assigned `RENDER_EXTERNAL_URL` for its public origin and allowed browser origin. Render supplies the listening `PORT`; the API binds on `0.0.0.0`.

## Account setup and deployment

1. Create your Render account, select a workspace and connect the GitHub account that can read this repository. A private repository is supported when Render has access.
2. Make sure the reviewed source changes are pushed to the branch you will deploy. Choose **New → Blueprint**, select the repository and that branch, and use `render.yaml` at the root.
3. Name the Blueprint **DBgram**, place it in the DBgram Production environment, and review the paid service/database/disk configuration before creating it.
4. Wait for PostgreSQL to become ready and the Docker build to finish. Flyway creates/migrates the schema automatically. There is no manual SQL setup step. Automatic deploys are disabled; use **Manual Deploy → Deploy latest commit** for reviewed updates.
5. Open the assigned `https://dbgram-….onrender.com` address. `/actuator/health` must return `{"status":"UP"}`. The frontend and `/api` share that HTTPS origin; no LAN address is required.
6. Verify registration, login, two independent accounts, public posts, comments, private content isolation, and upload persistence after a redeploy. Use the smoke scripts only on a disposable staging database because they create accounts/content.

If setting up resources manually rather than using the Blueprint, create a **Web Service**, choose **Docker**, and leave its root directory empty (repository root) with `./Dockerfile`. Create the database in the same region/workspace and set the Web Service's `DATABASE_URL` to its **Internal Database URL**, `DATABASE_SSL_MODE=require`, `JWT_SECRET` to a random secret of at least 32 bytes, `COOKIE_SECURE=true`, `SPRING_PROFILES_ACTIVE=portable`, `SERVER_ADDRESS=0.0.0.0`, `DBGRAM_STORAGE_PATH=/app/uploads`, and `DBGRAM_TRANSCODE_ENABLED=true`. Attach the disk before uploads are accepted. Let Render supply `PORT` and `RENDER_EXTERNAL_URL`.

## Custom domain and mobile clients

The generated HTTPS address works immediately; a custom domain is optional. After adding your domain in Render and following its DNS instructions, set `DBGRAM_PUBLIC_URL=https://YOUR_DOMAIN` and `CORS_ORIGINS=https://YOUR_DOMAIN`. Include the Render origin in `CORS_ORIGINS` too if you keep using both addresses. The web bundle uses `/api` and needs no rebuild when the domain changes.

For native builds, set `EXPO_PUBLIC_API_URL=https://YOUR_DOMAIN/api` in the frontend build environment. This is a public endpoint, not a credential. Native clients require an explicit URL because they do not run under the website's origin.

## Optional integrations and operations

Add Stripe secrets in the Render service environment only when configuring payments: `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, and `STRIPE_PRICE_ID`. The webhook URL is `/api/webhooks/stripe` under the public HTTPS origin. Validate with Stripe test mode before using live billing. Without these values, billing remains unavailable and does not simulate a successful purchase.

Set `DBGRAM_ADMIN_USER_IDS` to the UUIDs of trusted moderation operators after their accounts exist. Keep PostgreSQL backups and media backups outside this service, and test restores. Monitor storage usage, process memory, errors, database connections and transcoding work. The initial disk is durable across deploys, but it is not an unlimited storage service or a substitute for backups.

Once your account is ready, the information needed to finish is **the Render workspace/project, connected repository and deployment branch, and the service's assigned public URL**. A custom domain is optional. Database passwords, account passwords and JWT secrets should stay in Render.

This prepares a deployment; a live release is verified only after the Render build, HTTPS health check and account-isolation checks pass. Docker is unavailable in the current local Windows environment, so the image itself has not been run here.
