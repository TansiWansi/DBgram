package com.dbgram.service;

import com.dbgram.util.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real PostgreSQL checks in an isolated schema; no application accounts are touched. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class AccountPrivacyStoryTest {
    private final String schema = "qa_privacy_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate bootstrap;
    private JdbcTemplate db;
    private SocialService social;
    private StoryService stories;
    private CursorFeedService feeds;
    private DataSourceTransactionManager transactions;
    private TransactionStatus transaction;
    private UUID owner;
    private UUID follower;
    private UUID stranger;

    @BeforeAll
    void migrateIsolatedSchema() {
        var source = new DriverManagerDataSource(
                System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"),
                System.getenv("TEST_DATABASE_PASSWORD"));
        bootstrap = new JdbcTemplate(source);
        bootstrap.execute("CREATE SCHEMA " + schema);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema + ",public");
        source.setConnectionProperties(properties);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();
        db = new JdbcTemplate(source);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        social = new SocialService(db, mapper, mock(MediaUploadService.class));
        stories = new StoryService(db, social);
        feeds = new CursorFeedService(db, social, mapper);
        transactions = new DataSourceTransactionManager(source);
    }

    @BeforeEach
    void createAccounts() {
        transaction = transactions.getTransaction(new DefaultTransactionDefinition());
        owner = account("owner");
        follower = account("follower");
        stranger = account("stranger");
    }

    @AfterEach
    void rollbackFixtures() {
        if (transaction != null) {
            transactions.rollback(transaction);
        }
    }

    @AfterAll
    void removeIsolatedSchema() {
        if (bootstrap != null && schema.matches("qa_privacy_[a-f0-9]{32}")) {
            bootstrap.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void pendingRequestsNeverGrantAccessAndApprovalIsIdempotent() {
        UUID post = post("STANDARD", "PUBLIC");
        social.accountPrivacy(owner, true);
        social.follow(follower, owner, true);
        social.follow(follower, owner, true);
        assertFalse(allowed(post, follower));
        assertEquals(true, social.profile(owner, follower).get("followRequested"));
        assertEquals(1, social.followRequests(owner).size());
        assertEquals(0L, social.profile(owner, owner).get("followerCount"));
        assertThrows(AppException.class, () -> social.connections(owner, stranger, true));

        social.respondFollowRequest(owner, follower, true);
        social.respondFollowRequest(owner, follower, true);
        assertTrue(allowed(post, follower));
        assertFalse(allowed(post, stranger));
        assertEquals(1L, social.profile(owner, owner).get("followerCount"));
        assertEquals(1L, social.profile(follower, follower).get("followingCount"));
        assertEquals(false, social.profile(owner, follower).get("followRequested"));

        social.follow(follower, owner, false);
        assertFalse(allowed(post, follower));
        assertEquals(0L, social.profile(owner, owner).get("followerCount"));
    }

    @Test
    void openingAnAccountDoesNotBroadenNarrowPostAudiences() {
        UUID publicPost = post("STANDARD", "PUBLIC");
        UUID privatePost = post("STANDARD", "PRIVATE");
        UUID closeFriendsPost = post("STANDARD", "CLOSE_FRIENDS");
        social.follow(follower, owner, true);
        social.accountPrivacy(owner, true);
        assertTrue(allowed(publicPost, follower));
        assertFalse(allowed(publicPost, stranger));
        social.accountPrivacy(owner, false);
        assertTrue(allowed(publicPost, stranger));
        assertFalse(allowed(privatePost, stranger));
        assertFalse(allowed(closeFriendsPost, follower));
        assertTrue(allowed(privatePost, owner));
    }

    @Test
    void declineCancelAndBlockRemovePendingRequestsWithoutChangingCounts() {
        social.accountPrivacy(owner, true);
        social.follow(follower, owner, true);
        social.respondFollowRequest(owner, follower, false);
        assertTrue(social.followRequests(owner).isEmpty());
        social.follow(follower, owner, true);
        social.follow(follower, owner, false);
        assertTrue(social.followRequests(owner).isEmpty());
        social.follow(follower, owner, true);
        social.block(owner, follower, true);
        assertTrue(social.followRequests(owner).isEmpty());
        assertThrows(AppException.class, () -> social.follow(follower, owner, true));
        assertEquals(0L, social.profile(owner, owner).get("followerCount"));
    }

    @Test
    void existingRankedPagesAndCoauthoredPostsRecheckAccountPrivacy() {
        UUID first = post("STANDARD", "PUBLIC");
        UUID second = post("STANDARD", "PUBLIC");
        var page = feeds.page(stranger, "ranked", null, 1, null, null);
        assertNotNull(page.nextCursor());
        db.update("INSERT INTO post_authors(post_id,user_id,role,contribution_pct) VALUES(?,?,'CO_AUTHOR',0)", first, follower);
        social.accountPrivacy(owner, true);
        assertFalse(allowed(first, stranger));
        assertFalse(allowed(second, stranger));
        assertTrue(feeds.page(stranger, "ranked", page.nextCursor(), 1, null, null).items().isEmpty());
        assertTrue(social.search(stranger, "privacy fixture").get("posts") instanceof List<?> posts && posts.isEmpty());
        assertTrue(social.posts(stranger, "discover", 0, 20, owner, null, null, null).isEmpty());
    }

    @Test
    void storiesHavePrivateViewReceiptsAndExpireAtTwentyFourHours() {
        UUID story = post("STORY", "PUBLIC");
        assertEquals(0L, social.profile(owner, owner).get("postCount"));
        assertTrue(social.posts(follower, "discover", 0, 20, null, null, null, null).isEmpty());
        assertTrue(feeds.page(follower, "ranked", null, 20, null, null).items().isEmpty());
        social.follow(follower, owner, true);
        assertEquals(1, stories.tray(follower).size());
        assertEquals(true, stories.tray(follower).getFirst().get("hasUnseen"));
        stories.viewed(follower, story);
        stories.viewed(follower, story);
        stories.viewed(owner, story);
        assertEquals(1, stories.viewers(owner, story).size());
        assertEquals(false, stories.tray(follower).getFirst().get("hasUnseen"));
        assertThrows(AppException.class, () -> stories.viewers(stranger, story));

        social.accountPrivacy(owner, true);
        assertThrows(AppException.class, () -> stories.byUser(stranger, owner));
        social.follow(follower, owner, false);
        assertThrows(AppException.class, () -> stories.viewed(follower, story));
        db.update("UPDATE posts SET published_at=NOW()-INTERVAL '24 hours' WHERE id=?", story);
        assertFalse(allowed(story, owner));
        assertTrue(stories.byUser(owner, owner).isEmpty());
        assertThrows(AppException.class, () -> social.requirePost(story, owner));
    }

    @Test
    void storyDeletionPreservesPostCountsAndRejectsUnprotectedMedia() {
        post("STANDARD", "PUBLIC");
        UUID story = post("STORY", "PUBLIC");
        social.deletePost(owner, story);
        assertEquals(1L, social.profile(owner, owner).get("postCount"));
        assertFalse(allowed(story, follower));
        assertThrows(AppException.class, () -> social.createPost(owner, Map.of(
                "postType", "STORY", "blocks", List.of(Map.of(
                        "type", "image", "data", Map.of("url", "https://example.com/photo.jpg"))))));
        assertThrows(AppException.class, () -> SocialService.validateStory(List.of(
                Map.of("type", "poll", "data", Map.of())), "STORY", null));
    }

    private UUID account(String name) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO users(id,username,email,password_hash,display_name) VALUES(?,?,?,?,?)",
                id, name, name + "@example.test", "unused-test-password", name);
        return id;
    }

    private UUID post(String type, String visibility) {
        return (UUID) social.createPost(owner, Map.of(
                "postType", type, "visibility", visibility,
                "blocks", List.of(Map.of("type", "text", "data", Map.of("content", "privacy fixture")))))
                .get("id");
    }

    private boolean allowed(UUID post, UUID viewer) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT check_post_access(?,?)", Boolean.class, post, viewer));
    }
}
