# Local Windows environment

The two Makefiles use the existing PowerShell launcher to build and run DBgram with portable PostgreSQL and Adminer. Docker is not required for this setup. Runtime tools, credentials, database files and logs stay in the ignored `.runtime` directory.

## Start this checkout

Run these commands in PowerShell from `C:\DBMSminiProject`:

```powershell
.\.runtime\make\bin\make.exe -C backend up
.\.runtime\make\bin\make.exe -C frontend up
```

`backend up` builds the JAR with Java 21, runs the Java tests, starts PostgreSQL, waits for the API health check and starts Adminer. `frontend up` runs lint, TypeScript checks and frontend tests, exports the web app and restarts its same-origin API proxy. Both commands return after startup; their services continue in the background with hidden windows.

| Service | Address | Details |
| --- | --- | --- |
| Frontend | http://localhost:8787 | Exported web app; `/api` is proxied to the backend |
| Backend health | http://localhost:8080/actuator/health | Must report `UP` |
| PostgreSQL | `localhost:55432` | Database and user: `dbgram` |
| Adminer | http://localhost:8082/?pgsql=127.0.0.1%3A55432&username=dbgram&db=dbgram&ns=public | PostgreSQL database browser |

For Adminer, use **PostgreSQL**, server **127.0.0.1:55432**, username **dbgram**, database **dbgram**, and the `DatabasePassword` value in `.runtime/local.json`. The link fills in everything except the password. You can copy the password locally without printing it:

```powershell
(Get-Content .runtime/local.json -Raw | ConvertFrom-Json).DatabasePassword | Set-Clipboard
```

`host.docker.internal` was inappropriate for this PHP process running directly on Windows. The corrected link uses loopback and has no trailing comma after `public`.

## Open DBgram from another device on the same Wi-Fi

The frontend listens on all IPv4 interfaces (`WEB_HOST=0.0.0.0`). Its startup output lists the current **Same Wi-Fi** URL, using the active adapter's address. Open that URL, including port `8787`, on the other device. `localhost` on a phone points to the phone, so use the URL printed by the laptop's launcher.

Allow the website through Windows Firewall once, from an **Administrator PowerShell** in this repository:

```powershell
.\.runtime\make\bin\make.exe -C frontend firewall
```

This idempotent target permits inbound TCP `8787` for the configured Node executable from `LocalSubnet`, on either Private or Public Wi-Fi profiles. PostgreSQL, Adminer and the direct API keep their loopback bindings. If you change `WEB_PORT` or the Node installation, run the firewall target again with the new configuration.

Requests, uploads and refresh cookies use the same website origin. The proxy preserves the browser's host and supplies its own forwarding headers, so changing Wi-Fi addresses does not require a rebuild or a new CORS entry. Unrelated cross-origin websites remain blocked.

Keep the laptop awake and both services running. Devices must be on the same reachable network; guest networks or router client isolation can prevent devices from connecting even when they share a Wi-Fi name. Restart the frontend to print the current URL if the network address changes:

```powershell
.\.runtime\make\bin\make.exe -C frontend restart
```

## Build, restart and stop

```powershell
# Reuse the existing builds after a reboot.
.\.runtime\make\bin\make.exe -C backend start
.\.runtime\make\bin\make.exe -C frontend start

# Inspect processes or non-secret environment settings.
.\.runtime\make\bin\make.exe -C backend status
.\.runtime\make\bin\make.exe -C backend env

# Stop the frontend, then the API, Adminer and database.
.\.runtime\make\bin\make.exe -C frontend stop
.\.runtime\make\bin\make.exe -C backend down
```

Both Makefiles also have `build` and `test` targets. The frontend has `check`, `lint`, `typecheck`, `restart`, `firewall` and `dev` targets. The backend has `db` and `adminer` targets to start those services independently. `backend stop` leaves PostgreSQL running; `backend down` stops it gracefully and preserves its data. Backend builds stop only the API recorded by this launcher so Windows can replace the JAR. Use `backend up` to build and restart in one command.

Startup is safe to repeat. The launcher checks saved process identity and occupied ports before starting or stopping a service. Logs are `.runtime/api.log`, `.runtime/api-error.log`, `.runtime/web.log`, `.runtime/web-error.log`, `.runtime/adminer-error.log` and `.runtime/postgres.log`.

## Configuration

The launcher selects Java 21 explicitly for Maven and for the API process. It loads `DATABASE_PASSWORD` and `JWT_SECRET` from `.runtime/local.json`, generating credentials only if that file does not exist. Keep this file with the database it initialized; changing the file does not change an existing PostgreSQL role's password.

The local settings are:

```text
SPRING_PROFILES_ACTIVE=portable
DATABASE_URL=jdbc:postgresql://localhost:55432/dbgram
DATABASE_USER=dbgram
PORT=8080
SERVER_ADDRESS=127.0.0.1
COOKIE_SECURE=false
DBGRAM_PUBLIC_URL=http://localhost:8787
WEB_HOST=0.0.0.0
WEB_PORT=8787
BACKEND_URL=http://localhost:8080
EXPO_PUBLIC_API_URL=/api
```

Spring datasource variables are set explicitly as well, preventing stale shell settings from overriding the local database. Same-origin browser requests work through the proxy at any reachable hostname or address; CORS also permits the explicit local development origins. Uploads are stored under `.runtime/media`. Optional Stripe credentials are not required to boot; payment integrations still require their own configuration.

Override ports consistently in both commands, after stopping the current services:

```powershell
.\.runtime\make\bin\make.exe -C backend up DATABASE_PORT=55433 API_PORT=8090 WEB_PORT=8790 ADMINER_PORT=8092
.\.runtime\make\bin\make.exe -C frontend up API_PORT=8090 WEB_PORT=8790
```

The browser build uses relative `/api` requests and contains no machine-specific network IP. `frontend dev` optionally runs Expo on port 8081 with the configured localhost API port. Native devices need `EXPO_PUBLIC_API_URL` set to a reachable API hostname; localhost on a phone refers to the phone itself.

Use `frontend up` for shared Wi-Fi browser access. Set `WEB_HOST=127.0.0.1` on `frontend up` if you intentionally want a laptop-only preview. The launcher's equivalent option is `-WebBindAddress`.

## Tools on a fresh Windows checkout

This checkout has the tools below configured. On another machine, install or extract their Windows builds first:

- Java 21 JDK and Maven; current locations are `C:/tools/jdk-21.0.5+11` and `C:/tools/maven`.
- Node.js 24, including npm. Run `npm ci` in `frontend` once to install the project's existing dependencies.
- PowerShell 7 (`pwsh`).
- PostgreSQL 16 Windows binaries, with `pg_ctl.exe` under `.runtime/postgres/pgsql/bin`.
- [GNU Make 4.4.1 Windows x64](https://github.com/mbuilov/gnumake-windows), saved as `.runtime/make/bin/make.exe`.
- [PHP 8.4.26 NTS Windows x64](https://www.php.net/downloads.php?os=windows&version=8.4), extracted so that `.runtime/php/php.exe` exists. Its ZIP SHA-256 is `da68394f9193b7f6b89d0c76861a4034ae10efee7fd55a7255d8118c2acf70d7`.
- [Adminer 6.1.1 English](https://github.com/vrana/adminer/releases/download/v6.1.1/adminer-6.1.1-en.php), saved as `.runtime/adminer/index.php`. Use the full database build, not the MySQL-only build.

`deploy/adminer.ini` enables the bundled PostgreSQL PHP extensions. The launcher supplies absolute extension/session paths and binds the PHP development server to loopback. This is a local database tool, not a public hosting configuration.

Set tool paths in the ignored `.runtime/tools.mk`, using forward slashes and no surrounding quotes:

```makefile
POWERSHELL := C:/path/to/pwsh.exe
JAVA_HOME := C:/path/to/jdk-21
MAVEN_HOME := C:/path/to/maven
NPM := C:/Program Files/nodejs/npm.cmd
```

Without this file, the Makefiles use `pwsh` and `npm.cmd` from PATH, and the launcher checks `JAVA_HOME` / `MAVEN_HOME`. Command-line Make variables can override the local file.

## Docker alternative

When Docker is available, the root `compose.yml` already contains an optional Adminer service. Configure the root `.env` using `.env.example`, then run:

```powershell
docker compose --profile tools up -d postgres adminer
```

For that container environment, Adminer's server is **postgres:5432**, with database/user **dbgram** and password from `POSTGRES_PASSWORD` in `.env`. It uses a separate Docker volume, not the portable Windows database. Stop portable Adminer before using the same host port 8082. The separate distributed development stack in `backend/docker-compose.yml` maps PostgreSQL host port 55432 to container port 5432; stop portable PostgreSQL before running that stack on the same port.
