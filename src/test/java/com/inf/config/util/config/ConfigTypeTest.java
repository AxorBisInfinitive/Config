package com.inf.config.util.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** 读盘期的类型与值域严格性：越界、非整数、超 long、枚举未知名都要抛。 */
class ConfigTypeTest {

    @AfterAll
    static void cleanUp() {
        ConfigTestSupport.cleanup();
    }

    private static void rejects(String name, String valueJson, Consumer<Config> declare) throws IOException {
        Path file = ConfigTestSupport.path(name);
        ConfigTestSupport.reset(file);
        ConfigTestSupport.writeValue(file, valueJson);
        Config config = ConfigTestSupport.config(name);
        declare.accept(config);
        assertThrows(RuntimeException.class, config::init);
    }

    @Test
    void intAboveRangeThrows() throws IOException {
        rejects("type-int-above", "3000000000", c -> c.setInt("v").define(1).build());
    }

    @Test
    void intBelowRangeThrows() throws IOException {
        rejects("type-int-below", "-3000000000", c -> c.setInt("v").define(1).build());
    }

    @Test
    void shortAboveRangeThrows() throws IOException {
        rejects("type-short", "70000", c -> c.setShort("v").define((short) 1).build());
    }

    @Test
    void byteAboveRangeThrows() throws IOException {
        rejects("type-byte", "200", c -> c.setByte("v").define((byte) 1).build());
    }

    @Test
    void longBeyondRangeThrows() throws IOException {
        rejects("type-long-above", "9223372036854775808", c -> c.setLong("v").define(1L).build());
    }

    @Test
    void longFractionThrows() throws IOException {
        rejects("type-long-fraction", "1.5", c -> c.setLong("v").define(1L).build());
    }

    @Test
    void doubleInfinityThrows() throws IOException {
        rejects("type-double-infinity", "1e400", c -> c.setDouble("v").define(1.0).build());
    }

    @Test
    void unknownEnumNameThrows() throws IOException {
        rejects("type-enum", "\"nope\"", c -> c.setEnum("v", Mode.class).define(Mode.FAST).build());
    }

    @Test
    void nonNumberForIntThrows() throws IOException {
        rejects("type-not-number", "\"text\"", c -> c.setInt("v").define(1).build());
    }

    enum Mode {
        FAST,
        SLOW
    }
}
