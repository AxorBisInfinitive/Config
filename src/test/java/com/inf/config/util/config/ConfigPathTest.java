package com.inf.config.util.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** {@link Config#resolve(String)} 的判据：永远追加 .json、禁空白、禁绝对、禁逃逸。 */
class ConfigPathTest {

    @AfterAll
    static void cleanUp() {
        ConfigTestSupport.cleanup();
    }

    private static Path base() {
        return Path.of(System.getProperty("user.dir"), "config").normalize();
    }

    @Test
    void appendsJsonToPlainName() {
        assertEquals(base().resolve("plain.json"), Config.resolve("plain"));
    }

    @Test
    void appendsJsonToNestedPath() {
        assertEquals(base().resolve("nested").resolve("dir.json"), Config.resolve("nested/dir"));
    }

    @Test
    void appendsJsonEvenWhenNameAlreadyEndsWithIt() {
        assertEquals(base().resolve("doubled.json.json"), Config.resolve("doubled.json"));
    }

    @Test
    void normalizesInnerDotDot() {
        assertEquals(base().resolve("a").resolve("c.json"), Config.resolve("a/b/../c"));
    }

    @Test
    void rejectsNullEmptyAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> Config.resolve(null));
        assertThrows(IllegalArgumentException.class, () -> Config.resolve(""));
        assertThrows(IllegalArgumentException.class, () -> Config.resolve("   "));
    }

    @Test
    void rejectsAbsolutePaths() {
        assertThrows(IllegalArgumentException.class, () -> Config.resolve("/absolute/x"));
        assertThrows(IllegalArgumentException.class, () -> Config.resolve("C:\\absolute\\x"));
    }

    @Test
    void rejectsEscapes() {
        assertThrows(IllegalArgumentException.class, () -> Config.resolve("../outside"));
        assertThrows(IllegalArgumentException.class, () -> Config.resolve("a/../../outside"));
    }

    @Test
    void treatsBackslashAsSeparatorOnWindows() {
        assumeTrue(File.separatorChar == '\\');
        assertEquals(Config.resolve("nested/dir"), Config.resolve("nested\\dir"));
    }
}
