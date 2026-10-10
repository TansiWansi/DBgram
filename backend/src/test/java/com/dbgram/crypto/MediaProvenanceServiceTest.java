package com.dbgram.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MediaProvenanceServiceTest {
    @TempDir
    Path directory;
    private final MediaProvenanceService service = new MediaProvenanceService();

    @Test
    void everyOddTreeLeafHasAReproducibleProof() throws Exception {
        byte[] bytes = new byte[2 * MediaProvenanceService.LEAF_BYTES + 17];
        new Random(481).nextBytes(bytes);
        Path path = directory.resolve("fixture.bin");
        Files.write(path, bytes);
        var manifest = service.inspect(path);
        assertEquals(service.computeSha256(bytes), manifest.sha256());
        assertEquals(3, manifest.leaves().size());
        for (int leaf = 0; leaf < 3; leaf++) {
            String hash = manifest.leaves().get(leaf);
            for (Map<String, String> step : service.proof(manifest.leaves(), leaf)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String left = step.get("side").equals("left") ? step.get("hash") : hash;
                String right = step.get("side").equals("left") ? hash : step.get("hash");
                digest.update(HexFormat.of().parseHex(left));
                digest.update(HexFormat.of().parseHex(right));
                hash = HexFormat.of().formatHex(digest.digest());
            }
            assertEquals(manifest.merkleRoot(), hash);
            assertTrue(service.verify(manifest.leaves().get(leaf), manifest.merkleRoot(),
                    service.proof(manifest.leaves(), leaf)));
        }
        bytes[3] ^= 1;
        Files.write(path, bytes);
        assertNotEquals(manifest.merkleRoot(), service.inspect(path).merkleRoot());
    }

    @Test
    void emptyFileAndInvalidProofIndexAreHandled() throws Exception {
        Path empty = Files.createFile(directory.resolve("empty"));
        var manifest = service.inspect(empty);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", manifest.sha256());
        assertEquals(manifest.sha256(), manifest.merkleRoot());
        assertTrue(service.proof(manifest.leaves(), 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.proof(manifest.leaves(), 1));
        assertFalse(service.verify(manifest.sha256(), "0".repeat(64), List.of()));
        assertFalse(
                service.verify(manifest.sha256(), manifest.merkleRoot(), List.of(Map.of("hash", manifest.sha256()))));
    }
}
