package com.inf.config.util.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** 关键字形式的值在加载与回写之后必须仍是关键字名，不被冻结成解析后的数字。 */
class ConfigKeywordTest {

    @AfterAll
    static void cleanUp() {
        ConfigTestSupport.cleanup();
    }

    @Test
    void keywordNameSurvivesFirstLoad() throws IOException {
        Path file = ConfigTestSupport.path("keyword-load");
        ConfigTestSupport.reset(file);

        Config config = ConfigTestSupport.config("keyword-load");
        ConfigEntry<Integer> threads = config.setInt("threads")
                .keyword("auto", () -> 8)
                .comment("en_us", "Worker threads")
                .define("auto")
                .build();
        config.init();

        assertEquals(Integer.valueOf(8), threads.get());
        assertTrue(threads.isKeyword());
        assertEquals("auto", threads.keyword());
        String text = ConfigTestSupport.read(file);
        assertTrue(text.contains("\"value\": \"auto\""), text);
        assertTrue(text.contains("\"default\": \"auto\""), text);
    }

    @Test
    void keywordNameSurvivesRewrite() throws IOException {
        Path file = ConfigTestSupport.path("keyword-rewrite");
        ConfigTestSupport.reset(file);

        Config first = ConfigTestSupport.config("keyword-rewrite");
        first.setInt("threads").keyword("auto", () -> 8).define("auto").build();
        first.init();

        // 手改 default，制造代码侧声明漂移，下一次加载必须触发回写。
        String text = ConfigTestSupport.read(file);
        ConfigTestSupport.write(file, text.replace("\"default\": \"auto\"", "\"default\": 4"));

        Config second = ConfigTestSupport.config("keyword-rewrite");
        ConfigEntry<Integer> threads = second.setInt("threads").keyword("auto", () -> 8).define("auto").build();
        second.init();

        assertEquals("auto", threads.keyword());
        String rewritten = ConfigTestSupport.read(file);
        assertTrue(rewritten.contains("\"value\": \"auto\""), rewritten);
        assertTrue(rewritten.contains("\"default\": \"auto\""), rewritten);
    }

    @Test
    void keywordDerivedValueSkipsRangeCheck() throws IOException {
        Path file = ConfigTestSupport.path("keyword-range");
        ConfigTestSupport.reset(file);

        Config config = ConfigTestSupport.config("keyword-range");
        ConfigEntry<Integer> threads = config.setInt("threads")
                .range(1, 4)
                .keyword("auto", () -> 64)
                .define("auto")
                .build();
        config.init();

        assertEquals(Integer.valueOf(64), threads.get());
    }
}
