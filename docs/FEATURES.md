# DBgram feature brief and acceptance criteria

The user supplied this 14-phase scope on 2026-09-29 because the original checkout did not contain a README. This document preserves that scope; it does not assert that every requirement has shipped.

1. **Persistence and infrastructure:** Java 21 / Spring Boot 3 modular monolith; PostgreSQL/Flyway for users, communities, tiers, JSONB posts, immutable versions, coauthors, ACLs, billing, marketplace and copyright; Cassandra message/like/feed/notification/read-receipt tables; Redis; Kafka; Elasticsearch; S3/MinIO; Debezium CDC.
2. **Feeds and communities:** hybrid push/pull fan-out at one million followers; temporal engagement scoring; cursor pagination; timed communities with automatic expiry and cleanup.
3. **Messaging:** client-side end-to-end encryption, identity keys, signed and single-use prekeys; authenticated realtime delivery; delivery/read receipts; presence; disappearing messages; 20-second delete undo.
4. **Media:** TUS creation, resume, termination and checksums; S3 chunk storage; FFmpeg HLS/DASH variants; SHA-256 hashes, Merkle proofs and independently verifiable L2 anchoring.
5. **Structured collaboration:** validated text/carousel/poll/code/embed blocks; multiple authors whose shares sum to 100%; immutable edit snapshots, diffs/revert; public/followers/close-friends/community/custom ACLs; idempotent offline interaction batch sync.
6. **Search and monetization:** Elasticsearch autocomplete, weighted/fuzzy user/post/tag/community search; Stripe subscription checkout and verified idempotent webhooks; copyright takedown/counterclaim/resolution; unique-view and engagement analytics.
7. **Design system:** OLED dark theme, expressive accents, responsive mobile/desktop routing, accessible primitives, adaptive feed navigation, loading and error states.
8. **Structured feed:** dynamic memoized block rendering, persisted likes/saves with optimistic state, post details, version history and revert.
9. **Reels:** vertical video, zoom/double-tap likes, encrypted offline media cache, streaming/cache status, temporal-like animation.
10. **Messaging UI:** clear encryption status, receipts, view-once/disappearing media, reply gestures, countdowns and undo-delete toast.
11. **Composer:** reorderable blocks, persistent offline drafts, upload progress/resume, ACL picker, provenance controls.
12. **Community UI:** permanent/timed communities, countdown, paid-content gate, events, Google Meet creation/join, RSVPs.
13. **Discovery:** debounced sectioned search, encrypted search history, trending tags and creators, visual discovery grid.
14. **Trust and insights:** report/copyright forms and evidence, creator dashboard with real timeseries, audience and top-performing posts.

## Verification requested

- Backend unit and database integration tests; frontend lint, typecheck and production export.
- Two or more real accounts: persisted content, feed, engagement, independent sessions, and cross-account authorization isolation.
- Private content and histories excluded from unauthorized reads/search/feed/media.
- TUS resume/checksum test, FFmpeg output, cryptographic proof verification.
- No server plaintext for any feature advertised as E2EE.
- Measured query latency and rendering performance before making numeric performance claims.
- Live HTTPS deployment and public health check before calling the server deployed.

## External configuration

Production hosting access/domain, Stripe account products and keys, Google Workspace OAuth, and an L2 network/wallet are not present in this checkout. These integrations must fail clearly when unconfigured. A prepared adapter or a hash is not a completed payment, Google Meet meeting or blockchain transaction.

The existing Maven build is retained rather than replacing it with Gradle solely to match the proposal. The deployable baseline uses PostgreSQL for durable multi-user state. Distributed infrastructure is a separate integration track and must not be presented as validated global scale without running and testing it.
