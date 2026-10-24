package com.silver.branchreset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WipeFilesTest {
    @Test
    void removesOnlyThatPlayersFiles(@TempDir Path root) throws Exception {
        Path adv = Files.createDirectory(root.resolve("advancements"));
        Path stats = Files.createDirectory(root.resolve("stats"));
        Path data = Files.createDirectory(root.resolve("playerdata"));
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        for (Path dir : List.of(adv, stats)) {
            Files.writeString(dir.resolve(me + ".json"), "{}");
            Files.writeString(dir.resolve(other + ".json"), "{}");
        }
        Files.writeString(data.resolve(me + ".dat"), "x");
        Files.writeString(data.resolve(me + ".dat_old"), "x");
        Files.writeString(data.resolve(other + ".dat"), "x");
        List<String> removed = WipeFiles.erase(me, adv, stats, data);
        assertEquals(4, removed.size());
        assertFalse(Files.exists(adv.resolve(me + ".json")));
        assertTrue(Files.exists(adv.resolve(other + ".json")));
        assertTrue(Files.exists(data.resolve(other + ".dat")));
        assertEquals(List.of(), WipeFiles.erase(me, adv, stats, data), "second run finds nothing");
    }
}
