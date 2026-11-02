package com.p2p.server.social;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class UserResourceExporterTest {
    @TempDir
    Path tempDir;

    private SocialDatabase db;
    private SocialService service;

    @BeforeEach
    void setUp() throws Exception {
        db = new SocialDatabase(tempDir.resolve("test-social.db"));
        service = new SocialService(db);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) db.close();
    }

    @Test
    void testDirectExportWithoutCrashing() {
        // Register users
        assertDoesNotThrow(() -> {
            service.register(Map.of("username", "testuser1", "displayName", "Tester One", "password", "1234567890"));
            service.register(Map.of("username", "testuser2", "displayName", "Tester Two", "password", "1234567890"));
        });

        // Trigger direct sync
        assertDoesNotThrow(() -> {
            UserResourceExporter.syncDirect(db);
        });
    }
}
