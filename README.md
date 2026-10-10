<<<<<<< HEAD
# DBgram

A social app built with Java 21 / Spring Boot, PostgreSQL and Expo / React Native. The current **portable** runtime supports real accounts and persistent social data using PostgreSQL and durable media storage. Its responsive web frontend and API can share one HTTPS origin.

The complete 14-phase product brief is preserved in [docs/FEATURES.md](docs/FEATURES.md). That brief is the roadmap; the implementation status below distinguishes working features from planned infrastructure and integrations.

## Run locally

The Windows Makefiles select Java 21, build both applications and start PostgreSQL, the API, the frontend proxy and Adminer. Portable tools are configured in ignored `.runtime/tools.mk`; database credentials and the JWT secret are stored in ignored `.runtime/local.json`. See [the local setup guide](docs/LOCAL.md) for tool installation, environment settings, ports and shutdown commands.

```powershell
# From the repository root
.\.runtime\make\bin\make.exe -C backend up
.\.runtime\make\bin\make.exe -C frontend up
```

Open [DBgram](http://localhost:8787), [API health](http://localhost:8080/actuator/health), or [Adminer](http://localhost:8082/?pgsql=127.0.0.1%3A55432&username=dbgram&db=dbgram&ns=public). PostgreSQL listens on `localhost:55432`; database and username are `dbgram`. Adminer's password is `DatabasePassword` in `.runtime/local.json`. The launcher waits for readiness and keeps the services running in the background.

For another device on the same Wi-Fi, open the **Same Wi-Fi** URL printed by the frontend launcher. The website binds to `0.0.0.0` and proxies `/api`, so login and session refresh follow whichever address you open. Run `.\.runtime\make\bin\make.exe -C frontend firewall` once from an Administrator terminal to permit the website port from the local subnet. See [Wi-Fi access instructions](docs/LOCAL.md#open-dbgram-from-another-device-on-the-same-wi-fi).

For a separately running PostgreSQL instance, set `JWT_SECRET`, `DATABASE_URL`, and `COOKIE_SECURE=false` for local HTTP, then run `mvn spring-boot:run` in `backend`. `DATABASE_URL` accepts a provider PostgreSQL URI with credentials, or JDBC format with separate `DATABASE_USER` / `DATABASE_PASSWORD`. To serve the web export through Java, set `SPRING_WEB_RESOURCES_STATIC_LOCATIONS` to its absolute `file:.../frontend/dist/` location.

For the separate development proxy, run `node scripts/serve-web.mjs` and open http://localhost:8787. Configure it with `WEB_HOST`, `WEB_PORT`, and `BACKEND_URL`. The launcher supports `-ApiPort`, `-WebPort`, `-ApiBindAddress`, `-WebBindAddress` and `-AppOrigin`.

## Deploy

For a new hosting account, use [the Render setup guide](docs/RENDER.md) and the root [render.yaml](render.yaml). It provisions a Docker Web Service, managed PostgreSQL and a durable upload disk. Database credentials and JWT secrets are generated and injected by Render. The default Docker target includes both the web export and API.

For an existing Linux host, [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) covers the root [compose.yml](compose.yml), Caddy HTTPS, backups and updates. `.env.example` lists the settings; real `.env` files are ignored and excluded from Docker builds.

The browser uses relative `/api` requests. Uploads and media follow that origin, and database connections use hostnames. **No LAN IP is embedded in the application.** Native builds require `EXPO_PUBLIC_API_URL=https://YOUR_DOMAIN/api`. On Render, the public URL and allowed origin default to the generated `RENDER_EXTERNAL_URL`; custom domains use `DBGRAM_PUBLIC_URL` and `CORS_ORIGINS`.

Deployment configuration is prepared locally. A public deployment still needs the reviewed source pushed to the connected repository, an authenticated hosting account, and successful live build/health/account-isolation verification. The current local environment has no Docker runtime.

## Implemented in the portable runtime

| Area | API and frontend behavior |
| --- | --- |
| Accounts | Registration/login, password hashing, short-lived access tokens, rotating refresh sessions, HttpOnly web refresh cookies, native secure token storage, logout and account-scoped client state |
| People | Editable profiles, follow/unfollow, close friends, blocking and creator discovery |
| Feed | Ranked and chronological feeds, following/saved views, cursor pagination, engagement scoring with time decay, stable feed snapshots |
| Structured posts | Text, code, image/carousel, embed and poll blocks; composer drafts; media uploads; votes, comments, likes and bookmarks |
| Collaboration | Invited coauthors, acceptance, contribution allocation, version-conflict checks, content history and revert |
| Privacy | Public, followers, close friends, community, custom audience and private access; editable audience circles; access checks on feeds, search, history and media |
| Communities | Membership, private/paid gates, temporal communities, events, RSVP/capacity handling and meeting links |
| Messaging | Standard direct messages, read state, replies, disappearing messages, deletion and an undo window; participant isolation |
| Media | Owner-scoped resumable TUS uploads, checksum/offset checks, termination, signed and access-checked media, byte ranges, SHA-256/Merkle proofs and an FFmpeg processing worker |
| Discovery | PostgreSQL-backed search across people, posts and communities, plus suggested people and discovery screens |
| Offline interactions | Account-scoped interaction queue and idempotent batch sync, including conflict handling |
| Billing | Stripe checkout configuration and verified, idempotent subscription webhooks controlling paid membership; frontend checkout flow |
| Trust and insights | Reports, copyright evidence/counterclaims/moderation workflow, notifications, distinct views and engagement analytics |
| Interface | Saved Light/Dark themes (Dark by default), responsive navigation, safe-area layouts, glass surfaces, feed tabs, discovery, composer, reels, messages, communities, privacy controls and insights |

Payments require configured Stripe credentials and validation in test mode before live use. Proofs explicitly report `anchored: false` until an actual blockchain transaction integration exists. Media transcoding is included in Docker but has not been exercised with FFmpeg in the local Windows verification.

## Remaining roadmap

The portable app is not the complete enterprise architecture in the brief. Remaining work includes:

- Audited client-side E2EE, device identity/prekey lifecycle, encrypted key recovery and encrypted WebSocket messaging. Current direct messages are standard messaging, and the old key/WebSocket routes are disabled.
- Integrated Cassandra, Redis, Kafka fan-out, Elasticsearch and Debezium CDC, shared object storage, and verified horizontal scaling. Original distributed scaffolding exists behind the `distributed` profile; it is not the verified default runtime.
- WatermelonDB encrypted offline video/drafts, complete native upload/payment flows, view-once encrypted media, and the full gesture/animation specification.
- Real Google Workspace meeting creation, L2 anchoring, creator marketplace, OAuth/2FA and the remaining GraphQL/gRPC interfaces.
- Production load testing, restore drills, delivery/recovery operations and independent security review before representing the system as globally scalable or privacy equivalent to an audited messenger.

A Render persistent disk supports the initial single-instance deployment. Shared media storage and distributed background processing are required before scaling the API across instances.

## Verification

```powershell
cd backend
mvn test
cd ../frontend
npm run lint
npm run typecheck
npm test
npm run build:web
cd ..
node scripts/hosting-smoke.mjs
node scripts/smoke.mjs
node scripts/features-smoke.mjs
node scripts/advanced-smoke.mjs
```

The API smoke scripts default to http://localhost:8080; set `DBGRAM_TEST_URL` for a different disposable test deployment. They create test users and content. `hosting-smoke.mjs` checks public pages/assets and protected API routes without creating accounts. For a nondefault allowed CORS origin, set `DBGRAM_TEST_ORIGIN`. The advanced signed-webhook fixtures require `DBGRAM_TEST_STRIPE_WEBHOOK_SECRET` matching the test server.

The Java tests cover authentication/session rotation, billing, content validation, media permissions, ranking, provenance and provider database URI conversion. Frontend tests cover domain-independent URLs and account-switch/refresh races. API smoke tests exercise multiple accounts against real PostgreSQL, including authorization, concurrent/idempotent operations, cursors, collaboration, signed media and payment event ordering. Build/test success alone does not establish production load capacity or complete the remaining roadmap.
=======
# DBgram — Next-Generation Social Media Platform

A globally-scalable social media ecosystem combining Instagram-quality media, Telegram-grade E2EE privacy, Notion-style structured posts, and Substack-like paid communities.

## User Review Required

> [!IMPORTANT]
> **Scale of this project**: This is an enterprise-grade distributed system with ~80+ source files across backend and frontend. Full implementation will be done incrementally across 14 phases (6 backend + 8 frontend). Each phase builds on the previous one.

> [!WARNING]
> **Infrastructure dependencies**: The backend requires PostgreSQL, Cassandra/ScyllaDB, Redis, Kafka, Elasticsearch, and S3-compatible storage. For local development, we'll use Docker Compose to orchestrate all services. Ensure Docker Desktop is installed.

> [!IMPORTANT]
> **Monorepo vs Polyrepo**: I propose a **monorepo** structure with `/backend` (Java/Spring Boot) and `/frontend` (React Native/Expo) at the root. Confirm if you prefer separate repositories.

## Open Questions

> [!IMPORTANT]
> 1. **Database credentials**: Should I use default dev credentials (postgres/postgres, etc.) or do you have specific ones?
> 2. **AWS vs Local**: For Phase 4 (Media Pipeline), should I mock S3 with MinIO locally, or configure real AWS credentials?
> 3. **Blockchain Layer 2**: Which L2 do you prefer for media provenance anchoring? (Polygon, Arbitrum, Base, or mock for now?)
> 4. **Stripe Keys**: Do you have Stripe test API keys, or should I stub the payment integration?
> 5. **Google Meet API**: Do you have Google Workspace API credentials for Meet integration, or should I stub it?

---

## Proposed Changes

### Project Structure

```
c:\DBMSminiProject\
├── backend/                          # Java 21 + Spring Boot 3 Monolith (Modular)
│   ├── build.gradle.kts              # Gradle build with all dependencies
│   ├── settings.gradle.kts
│   ├── docker-compose.yml            # Postgres, Cassandra, Redis, Kafka, ES, MinIO
│   ├── src/main/java/com/dbgram/
│   │   ├── DBgramApplication.java
│   │   ├── config/                   # Spring configs (Security, Kafka, Redis, WS, etc.)
│   │   ├── domain/                   # JPA Entities, Cassandra Entities, Records
│   │   │   ├── user/
│   │   │   ├── post/
│   │   │   ├── community/
│   │   │   ├── messaging/
│   │   │   ├── media/
│   │   │   ├── feed/
│   │   │   ├── search/
│   │   │   └── billing/
│   │   ├── repository/               # JPA + Spring Data Cassandra repos
│   │   ├── service/                  # Business logic services
│   │   ├── controller/               # REST/GraphQL/WebSocket controllers
│   │   ├── kafka/                    # Kafka producers & consumers
│   │   ├── worker/                   # Background workers (transcoding, TTL, etc.)
│   │   ├── security/                 # JWT, OAuth, 2FA, ACL logic
│   │   ├── crypto/                   # Merkle Tree, SHA-256, blockchain anchoring
│   │   └── util/                     # Shared utilities
│   └── src/main/resources/
│       ├── application.yml
│       ├── db/migration/             # Flyway SQL migrations
│       └── cql/                      # Cassandra CQL scripts
│
├── frontend/                         # React Native (Expo) + TypeScript
│   ├── app/                          # Expo Router file-based routing
│   │   ├── (tabs)/                   # Bottom tab navigator
│   │   │   ├── index.tsx             # Feed screen
│   │   │   ├── search.tsx            # Search/Discovery
│   │   │   ├── create.tsx            # Composer
│   │   │   ├── reels.tsx             # Full-screen Reels
│   │   │   └── profile.tsx           # Profile
│   │   ├── chat/                     # E2EE Messaging screens
│   │   ├── community/               # Community screens
│   │   ├── post/                     # Post detail, edit history
│   │   └── settings/                 # Settings, privacy, business
│   ├── components/                   # Reusable UI components
│   │   ├── blocks/                   # BlockRenderer, TextBlock, CarouselBlock, etc.
│   │   ├── feed/                     # FeedCard, AdaptiveFeedHeader
│   │   ├── reels/                    # ReelPlayer, TemporalLike
│   │   ├── chat/                     # SecureChat, EphemeralBubble, UndoToast
│   │   ├── composer/                 # BlockComposer, UploadManager, ACLPicker
│   │   ├── community/               # EventCard, PaywallGate, CountdownTimer
│   │   ├── search/                   # AutocompleteBar, TrendingGrid
│   │   ├── analytics/               # InsightsDashboard, ChartCards
│   │   └── ui/                       # Primitives (Button, Modal, GlassCard, etc.)
│   ├── hooks/                        # Custom hooks
│   ├── store/                        # Zustand stores
│   ├── services/                     # API clients, E2EE crypto, TUS upload
│   ├── db/                           # WatermelonDB models & schema
│   ├── theme/                        # Design tokens, colors, typography
│   ├── utils/                        # Shared utilities
│   ├── app.json                      # Expo config
│   ├── package.json
│   └── tsconfig.json
│
└── docs/                             # Architecture docs, API specs
```

---

## Backend Phases

### Phase 1: Polyglot Database Schemas & Domain Entities

#### [NEW] [build.gradle.kts](file:///c:/DBMSminiProject/backend/build.gradle.kts)
Gradle build file with dependencies:
- Spring Boot 3.3+, Spring Web, Spring Data JPA, Spring Data Cassandra
- Spring Security, Spring WebSocket, Spring Kafka
- PostgreSQL driver, Flyway migrations
- Redis (Lettuce), Elasticsearch client
- Jackson (JSONB), Hibernate Types
- gRPC Spring Boot Starter, GraphQL Spring Boot
- JWT (jjwt), Bouncy Castle (crypto)
- Lombok, MapStruct

#### [NEW] [docker-compose.yml](file:///c:/DBMSminiProject/backend/docker-compose.yml)
Docker Compose orchestrating:
- PostgreSQL 16 (port 5432)
- Cassandra 4 (port 9042)
- Redis 7 Cluster (port 6379)
- Kafka + Zookeeper (port 9092)
- Elasticsearch 8 (port 9200)
- MinIO (S3-compatible, port 9000)
- Debezium Connect (port 8083)

#### [NEW] PostgreSQL Flyway Migrations
- `V1__users.sql` — Users table with profile JSONB, verification, 2FA fields
- `V2__communities.sql` — Communities, CommunityMembers, paid tiers
- `V3__posts.sql` — Posts (JSONB `blocks` column), PostVersions (edit history), PostAuthors (multi-author with `contribution_pct`)
- `V4__access_control.sql` — ACL tables (PostACL, CloseFriends, CommunityACL) with covering indexes for <10ms lookups
- `V5__billing.sql` — Subscriptions, Payments, Listings (creator marketplace)
- `V6__copyright.sql` — CopyrightClaims, MediaHashes, CounterClaims

#### [NEW] JPA Entity Classes
- `User.java` — `@Entity` with JSONB profile, verification status, follower counts (denormalized)
- `Community.java` — Name, type (PERMANENT/TEMPORAL), tier, TTL for ephemeral groups
- `Post.java` — JSONB `blocks` column with custom Hibernate `JsonType`, visibility enum
- `PostVersion.java` — Immutable snapshots of post state for edit history
- `PostAuthor.java` — Join table with `contribution_pct` (sums to 100)
- `AccessControlEntry.java` — Polymorphic ACL (PUBLIC, CLOSE_FRIENDS, COMMUNITY_ONLY, CUSTOM)
- `CopyrightClaim.java` — Status workflow (PENDING → REVIEWING → UPHELD/REJECTED → COUNTER_CLAIMED)
- `Subscription.java`, `Listing.java` — Stripe-backed billing entities

#### [NEW] Cassandra CQL Schemas
- `messages_by_conversation` — Partitioned by `convo_id`, clustered by `created_at DESC`
- `likes_by_post` — With `created_at` for temporal decay calculations
- `feed_by_user` — Pre-computed feed partitioned by `user_id`, clustered by `score DESC`
- `notifications_by_user` — Partitioned by `user_id`, clustered by `created_at DESC`
- `read_receipts` — Per-conversation read status

#### [NEW] Spring Data Cassandra Entity Classes
- `CassandraMessage.java` — Record-style with `@Table`, encrypted blob storage
- `CassandraLike.java` — With temporal metadata
- `CassandraFeedEntry.java` — Pre-scored feed items
- `CassandraNotification.java`

#### Debezium CDC Configuration
- Kafka Connect JSON config for Postgres → Elasticsearch sync
- Connectors for `posts`, `users`, `communities` tables
- Elasticsearch sink connector with index mappings

---

### Phase 2: Hybrid Feed Engine, Fan-Out & Communities

#### [NEW] `FanOutKafkaConsumer.java`
- `@KafkaListener` consuming `post.created` events
- Hybrid logic: push to Redis sorted sets for users with <1M followers
- Celebrity (>1M) posts stored in a separate "celebrity posts" cache
- Uses Virtual Threads via `@Async` with `Executors.newVirtualThreadPerTaskExecutor()`

#### [NEW] `FeedService.java`
- Algorithmic decay scoring: `Score = e^(-λΔt) * AffinityWeights * EngagementRate`
- Read-time merge of push (from Redis) + pull (celebrity posts) feeds
- Pagination via cursor-based scrolling (no OFFSET)

#### [NEW] `TemporalCommunityService.java`
- Spring Data Redis operations for ephemeral event groups
- `EXPIREAT` for auto-destruction when event ends
- Cleanup job for orphaned data

---

### Phase 3: E2EE Messaging, Ephemerality & Sync

#### [NEW] `PreKeyController.java`
- REST endpoints: `POST /api/keys/bundle`, `GET /api/keys/{userId}/bundle`
- Stores Identity Keys, Signed PreKeys, One-Time PreKeys (consumed on fetch)
- Rate-limited key replenishment

#### [NEW] `MessageWebSocketHandler.java`
- Spring WebSocket using Virtual Threads for massive concurrency
- Routes encrypted blobs (never decrypts) to Cassandra + recipient WebSocket
- Delivery receipts, read receipts routing
- Online presence tracking via Redis pub/sub

#### [NEW] `EphemeralMessageService.java`
- 20-second soft-delete undo window using Redis `EXPIRE`
- `@Scheduled` cron job for disappearing messages TTL sweep
- Cassandra TTL for automatic row expiry

---

### Phase 4: Media Pipeline, TUS & Blockchain Provenance

#### [NEW] `TusUploadController.java`
- TUS 1.0 protocol implementation (Creation, Termination, Checksum extensions)
- Chunked upload to S3/MinIO with offset tracking in Redis
- Resume-after-disconnect support

#### [NEW] `TranscodingWorker.java`
- Kafka consumer triggered by `media.uploaded` events
- Spawns FFmpeg process for HLS/DASH transcoding (multiple bitrates)
- Uploads segments back to S3, updates media status in Postgres

#### [NEW] `MediaProvenanceService.java`
- SHA-256 hash verification of uploaded media
- Merkle Tree construction from batch of media hashes
- Root hash preparation for L2 blockchain anchoring
- `MerkleTree.java` utility with proof generation/verification

---

### Phase 5: Structured Posts, Collaboration & Access Control

#### [NEW] `PostController.java` (WebFlux)
- Reactive endpoint for structured post creation
- Block validation (Text, Carousel, Poll, Code, Embed)
- Multi-author contribution percentage enforcement (must sum to 100%)

#### [NEW] `PostVersionService.java`
- Automatic versioning on every edit (immutable snapshots)
- Diff computation between versions
- Revert endpoint restoring previous JSONB state

#### [NEW] `AccessControlService.java`
- High-speed ACL check: single SQL query with covering index (<10ms)
- Supports Public, Close Friends, Community-Only, Custom lists
- Cached in Redis with invalidation on ACL change

#### [NEW] `OfflineSyncController.java`
- Batch endpoint accepting array of offline events
- Async processing via Kafka for likes, watch-time, interactions
- Idempotency via client-generated event UUIDs

---

### Phase 6: Search, Monetization & Trust/Safety

#### [NEW] `SearchService.java`
- Elasticsearch Edge N-Gram query builder for autocomplete
- Weighted multi-field search (username, display_name, hashtags, post content)
- Fuzzy matching with typo tolerance

#### [NEW] `StripeWebhookController.java`
- Webhook handler for `customer.subscription.created/updated/deleted`
- Grants/revokes `CommunityMember` roles in Postgres
- Idempotent event processing with Stripe event ID deduplication

#### [NEW] `CopyrightClaimService.java`
- Full workflow: Takedown → Counter-claim → Resolution
- Evidence upload, automated hash comparison
- Status notifications via Kafka events

#### [NEW] `AnalyticsService.java`
- Redis HyperLogLog for unique view counts (memory-efficient)
- Kafka consumer aggregating engagement events
- Dashboard query endpoints with time-series bucketing

---

## Frontend Phases

### Phase 7 (FE Phase 1): Design System & Global Architecture

#### [NEW] [theme/](file:///c:/DBMSminiProject/frontend/theme/)
- `colors.ts` — OLED-black dark mode, neon gradient accents, glassmorphism tokens
- `typography.ts` — Inter/Outfit font system, scale from xs to 3xl
- `spacing.ts` — 4px grid system
- `shadows.ts` — Elevated glass effects
- `animations.ts` — Spring configs for Reanimated 3

#### [NEW] Navigation Architecture
- Expo Router file-based routing with `(tabs)` layout
- Bottom tabs: Feed, Search, Create (+), Reels, Profile
- Modal stacks for full-screen Reels, Chat, Post Detail
- Adaptive Feed Header with horizontal swipe (Chronological | Algorithmic | Community)

#### [NEW] UI Primitives
- `GlassCard.tsx` — Glassmorphism card with blur backdrop
- `GradientButton.tsx` — Animated gradient CTA buttons
- `AnimatedIcon.tsx` — Micro-animated tab bar icons
- `BottomSheet.tsx` — Reanimated bottom sheet modal
- `Skeleton.tsx` — Shimmer loading placeholders

---

### Phase 8 (FE Phase 2): Structured Block Feed

#### [NEW] `BlockRenderer.tsx`
- Dynamic renderer ingesting JSONB block arrays
- Renders: TextBlock, CarouselBlock, PollBlock, CodeBlock, EmbedBlock
- Memoized with `React.memo` for FlashList performance

#### [NEW] `FeedCard.tsx`
- Full post card with author avatar, blocks, engagement bar
- Swipe gestures for quick actions
- Optimistic UI updates for likes/saves

#### [NEW] `EditHistorySheet.tsx`
- Bottom sheet showing post version timeline
- Diff viewer highlighting changes between versions
- One-tap revert with confirmation

---

### Phase 9 (FE Phase 3): Offline-First Reels & Media

#### [NEW] `ReelPlayer.tsx`
- Full-screen vertical video with FlashList virtualization
- Pinch-to-zoom, double-tap-to-like with Reanimated
- Offline playback from WatermelonDB encrypted cache
- Network status indicator (cached vs. streaming)

#### [NEW] `TemporalLikeAnimation.tsx`
- Heart animation with visual decay based on post age
- Reanimated 3 shared values for smooth 60fps animation
- Opacity/scale driven by `e^(-λΔt)` decay formula

---

### Phase 10 (FE Phase 4): E2EE Messaging UI

#### [NEW] `SecureChatScreen.tsx`
- Encrypted chat interface with padlock indicators
- Distinct color theme for E2EE vs. standard chats
- Bubble layout with delivery/read receipts

#### [NEW] `EphemeralBubble.tsx`
- "View Once" media with visual countdown fuse (Reanimated circle)
- Disappearing message timer overlay

#### [NEW] `UndoDeleteToast.tsx`
- 20-second undo toast with animated progress bar
- Gesture-based dismiss

#### [NEW] `SwipeToReply.tsx`
- Gesture Handler swipe-right to quote-reply
- Haptic feedback + spring animation

---

### Phase 11 (FE Phase 5): Content Creation & Uploads

#### [NEW] `BlockComposer.tsx`
- Offline-first post composer with drag-to-reorder blocks
- Block type picker: Text, Image, Code, Poll, Carousel
- Auto-save drafts to WatermelonDB

#### [NEW] `UploadManager.tsx`
- Global upload progress indicator (persistent across screens)
- TUS protocol integration showing chunk progress
- Network drop detection with auto-resume UI

#### [NEW] `ACLPicker.tsx`
- Pre-publish access control selector
- Public / Close Friends / Specific Community / Custom list
- Blockchain provenance toggle with explanation tooltip

---

### Phase 12 (FE Phase 6): Communities & Events

#### [NEW] `TemporalCommunityCard.tsx`
- Event group card with animated countdown timer
- Auto-destruct visual effect when TTL expires

#### [NEW] `PaywallGate.tsx`
- Premium community preview with blurred content
- Native payment sheet (Stripe / IAP)

#### [NEW] `EventCard.tsx`
- In-feed event block with Google Meet one-tap join
- Participant avatars, time/date, RSVP status

---

### Phase 13 (FE Phase 7): Search & Discovery

#### [NEW] `AutocompleteBar.tsx`
- Global search with debounced Edge N-Gram queries
- Sectioned results: Users, Hashtags, Communities, Posts
- Recent searches with encrypted local storage

#### [NEW] `TrendingGrid.tsx`
- Visual discovery grid with trending hashtags and creators
- Masonry layout with animated entrance

---

### Phase 14 (FE Phase 8): Trust, Safety & Analytics

#### [NEW] `ReportSheet.tsx`
- Multi-step bottom sheet for content reporting
- Copyright claim flow with evidence upload
- Category selection with description

#### [NEW] `InsightsDashboard.tsx`
- Business profile analytics screen
- Time-series charts (react-native-skia) for views, saves, clicks
- Audience demographics, top-performing content cards

---

## Verification Plan

### Automated Tests
```bash
# Backend
cd backend && ./gradlew test                    # Unit tests
cd backend && ./gradlew integrationTest         # Testcontainers-based integration tests

# Frontend
cd frontend && npx expo run:web                 # Web preview for rapid iteration
cd frontend && npm test                          # Jest + React Native Testing Library
```

### Manual Verification
- Docker Compose `up` → all services healthy
- POST a structured post → verify JSONB storage + Elasticsearch sync
- Send E2EE message → verify encrypted blob in Cassandra, never plaintext on server
- Upload video → verify TUS resume, FFmpeg transcoding, HLS output
- Feed rendering → verify hybrid push/pull merge with decay scoring
- ACL check → verify <10ms query performance with EXPLAIN ANALYZE
- Frontend: Visual inspection of dark theme, animations at 60fps, offline mode

---

## Execution Order

| Order | Phase | Description | Est. Files |
|-------|-------|-------------|------------|
| 1 | Backend Phase 1 | Schemas, Entities, Docker, Gradle | ~25 files |
| 2 | Frontend Phase 1 | Design System, Navigation, Primitives | ~15 files |
| 3 | Backend Phase 2 | Feed Engine, Fan-Out, Communities | ~6 files |
| 4 | Frontend Phase 2 | Block Feed, FeedCard, EditHistory | ~8 files |
| 5 | Backend Phase 3 | E2EE Messaging, Ephemerality | ~6 files |
| 6 | Frontend Phase 4 | Chat UI, Ephemeral, Undo | ~6 files |
| 7 | Backend Phase 4 | Media Pipeline, TUS, Blockchain | ~6 files |
| 8 | Frontend Phase 3 | Reels, Offline Video, Temporal Like | ~5 files |
| 9 | Backend Phase 5 | Structured Posts, ACL, Offline Sync | ~6 files |
| 10 | Frontend Phase 5 | Composer, Upload Manager, ACL Picker | ~5 files |
| 11 | Backend Phase 6 | Search, Payments, Trust/Safety | ~6 files |
| 12 | Frontend Phase 6 | Communities, Events, Paywall | ~5 files |
| 13 | Frontend Phase 7 | Search UI, Discovery | ~4 files |
| 14 | Frontend Phase 8 | Reporting, Analytics Dashboard | ~4 files |

**Total: ~107 files across 14 execution phases**

> [!TIP]
> I recommend we proceed **one phase at a time** with review checkpoints. Each phase will produce working, testable code before moving to the next. Shall I begin with **Backend Phase 1** (Database Schemas, Entities, Docker, Gradle setup)?
>>>>>>> ce38cc19f6350cf0201cf822e6e9c08222930c6b
