package com.inf.config.util.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** 回写语义：结构不符抛、缺键补写、额外 note 保留、二次加载不改字节、越界抛。 */
class ConfigRewriteTest {

    @AfterAll
    static void cleanUp() {
        ConfigTestSupport.cleanup();
    }

    @Test
    void structureMismatchThrows() throws IOException {
        Path file = ConfigTestSupport.path("rewrite-structure");
        ConfigTestSupport.reset(file);
        ConfigTestSupport.write(file, "{\"settings\": {\"v\": 5}}");

        Config config = ConfigTestSupport.config("rewrite-structure");
        config.setInt("v").define(1).build();

        assertThrows(RuntimeException.class, config::init);
    }

    @Test
    void missingValueKeyThrows() throws IOException {
        Path file = ConfigTestSupport.path("rewrite-no-value");
        ConfigTestSupport.reset(file);
        ConfigTestSupport.write(file, "{\"settings\": {\"v\": {}}}");

        Config config = ConfigTestSupport.config("rewrite-no-value");
        config.setInt("v").define(1).build();

        assertThrows(RuntimeException.class, config::init);
    }

    @Test
    void missingKeysArePatched() throws IOException {
        Path file = ConfigTestSupport.path("rewrite-patch");
        ConfigTestSupport.reset(file);
        ConfigTestSupport.write(file, "{\"settings\": {\"v\": {\"value\": 3}}}");

        Config config = ConfigTestSupport.config("rewrite-patch");
        config.setInt("v").range(1, 10).comment("en_us", "text").define(1).build();
        config.init();

        String text = ConfigTestSupport.read(file);
        assertTrue(text.contains("\"value\": 3"), text);
        assertTrue(text.contains("\"default\": 1"), text);
        assertTrue(text.contains("\"max\": 10"), text);
        assertTrue(text.contains("\"en_us\": \"text\""), text);
    }

    @Test
    void unknownNoteKeysSurviveRewrite() throws IOException {
        Path file = ConfigTestSupport.path("rewrite-note");
        ConfigTestSupport.reset(file);
        ConfigTestSupport.write(file,
                "{\"settings\": {\"v\": {\"value\": 3, \"note\": {\"custom\": \"keep me\"}}}}");

        Config config = ConfigTestSupport.config("rewrite-note");
        config.setInt("v").comment("en_us", "fresh").define(1).build();
        config.init();

        String text = ConfigTestSupport.read(file);
        assertTrue(text.contains("\"custom\": \"keep me\""), text);
        assertTrue(text.contains("\"en_us\": \"fresh\""), text);
    }

    @Test
    void secondLoadKeepsBytesIdentical() throws IOException {
        Path file = ConfigTestSupport.path("rewrite-idempotent");
        ConfigTestSupport.reset(file);

        Config first = ConfigTestSupport.config("rewrite-idempotent");
        first.setInt("v").range(0, 100).comment("en_us", "text").define(7).build();
        first.init();
        String before = ConfigTestSupport.read(file);

        Config second = ConfigTestSupport.config("rewrite-idempotent");
        second.setInt("v").range(0, 100).comment("en_us", "text").define(7).build();
        second.init();

        assertEquals(before, ConfigTestSupport.read(file));
    }

    @Test
    void valueOutOfRangeThrows() throws IOException {
        Path file = ConfigTestSupport.path("rewrite-range");
        ConfigTestSupport.reset(file);
        ConfigTestSupport.writeValue(file, "70000");

        Config config = ConfigTestSupport.config("rewrite-range");
        config.setInt("v").range(1, 65536).define(1).build();

        assertThrows(RuntimeException.class, config::init);
    }
}
