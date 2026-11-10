package com.inf.config.util.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 测试共用的落盘工具。
 *
 * <p>被测代码的基准永远是 {@code config} 目录，所以测试文件只能落在 {@code config} 下。这里给每次
 * 运行一个唯一子目录，各测试类在 {@code @AfterAll} 里调 {@link #cleanup()} 删掉，跑完不在仓库里
 * 留文件。
 */
final class ConfigTestSupport {

    /** 本次运行的唯一相对根，落在 config/test 下。 */
    private static final String RUN = "test/" + System.nanoTime();

    /**
     * 本次运行的目录。由 {@link Config#resolve(String)} 算出的文件路径取父目录，
     * 这样目录基准与文件基准同源，不会因为 resolve 永远追加 .json 而把目录当成文件。
     */
    private static final Path ROOT = Config.resolve(RUN + "/run").getParent();

    private ConfigTestSupport() {
    }

    /** 该用例的文件，经被测的 {@link Config#resolve(String)} 算出，顺带覆盖它。 */
    static Path path(String name) {
        return ROOT.resolve(name + ".json");
    }

    /** 与该路径一致的 Config 实例，注释为空。 */
    static Config config(String name) {
        return new Config(RUN + "/" + name, Map.of());
    }

    /** 清掉上一次运行留下的同名文件。 */
    static void reset(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    /** 写一份最小配置：只有一个条目 v，值是给定的 JSON 片段。 */
    static void writeValue(Path file, String valueJson) throws IOException {
        write(file, "{\"settings\": {\"v\": {\"value\": " + valueJson + "}}}");
    }

    static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** 删掉本次运行写的整棵子树。多个测试类各调一次，第二次起是空操作。 */
    static void cleanup() {
        if (!Files.exists(ROOT)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(ROOT)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // 删不掉就留着，不影响判定
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
        try {
            Files.deleteIfExists(ROOT.getParent());
            Files.deleteIfExists(ROOT.getParent().getParent());
        } catch (IOException ignored) {
            // 目录非空就留着
        }
    }
}
