package com.inf.config.util.config;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 单个配置项：当前值、值类型，以及读盘期要用的元数据。
 *
 * <p>类型由 {@link #type()} 给出，取 int、long、short、byte、float、double、boolean、String 与 Enum 之一。
 *
 * <p>除值本身外还承载 builder 声明、读盘期使用的三类元数据：
 *
 * <ul>
 * <li>{@link Constraint}：值约束。只校验文件里显式写出的值，不校验关键字派生出来的结果，
 *     后者由代码算出，不是用户输入。两者混在一起，值域就既不是护栏也不是文档，
 *     还会制造用户无法修复的启动失败。
 * <li>{@code min} 与 {@code max}：约束在文件里的表示，两侧各自独立、可缺一侧，
 *     供手改配置的人对照，读盘期不参与校验。
 * <li>{@code keywords}：关键字名到取值器与注释的映射，读盘期按名反查。
 * </ul>
 *
 * @param <T> 值类型
 */
public final class ConfigEntry<T> {

    /** 值约束。越界抛 {@link IllegalArgumentException}，消息只含值域，文件与条目名由 {@link ConfigFile} 补。 */
    public interface Constraint<T> {
        void check(T value);
    }

    /** 一份关键字声明：取值器与它自己的注释。 */
    public record Keyword<T>(Supplier<T> supplier, Map<String, String> notes) {
    }

    /** 配置项名只允许 a-zA-Z0-9_-，空名字不合法。 */
    public static boolean isValidName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return name.length() > 0;
    }

    /** 条目名，在所属文件内唯一。 */
    private final String name;

    /** 注释来源到注释文本。 */
    private final Map<String, String> notes;

    /** 值类型，枚举项为枚举类本身。 */
    private final Class<?> type;

    /** 文件缺该条目、或文件尚未生成时写入的值。 */
    private final T defaultValue;

    /** 该条目声明的关键字，名字到声明的映射。 */
    private final Map<String, Keyword<T>> keywords;

    /** 默认值命中关键字时记其名字，否则 null。 */
    private final String defaultKeyword;

    /** 下界声明，未声明为 null。 */
    private final T min;

    /** 上界声明，未声明为 null。 */
    private final T max;

    /** 值域约束，未声明为 null。 */
    private final Constraint<T> constraint;

    /** 当前值，读盘之后即为文件里的值。 */
    private T value;

    /**
     * 当前值命中的关键字名。回写时据此写回关键字名而不是解析后的值；
     * 少了这个状态，任何一次回填重写都会把关键字静默冻结成数字。
     */
    private String valueKeyword;

    /** 由所属文件的 builder 构造，外部不要直接调用。 */
    public ConfigEntry(String name, Map<String, String> notes, Class<?> type, T defaultValue,
            String defaultKeyword, Map<String, Keyword<T>> keywords, T min, T max,
            Constraint<T> constraint) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid config entry name: %s".formatted(name));
        }
        this.name = name;
        this.notes = Map.copyOf(notes);
        this.type = type;
        this.defaultValue = defaultValue;
        this.defaultKeyword = defaultKeyword;
        this.keywords = Map.copyOf(keywords);
        this.min = min;
        this.max = max;
        this.constraint = constraint;
        this.value = defaultValue;
    }

    /** 条目名。 */
    public String name() {
        return name;
    }

    /** 该条目的注释，来源键到文本。 */
    public Map<String, String> notes() {
        return notes;
    }

    /** 值类型。 */
    public Class<?> type() {
        return type;
    }

    /** 默认值。 */
    public T defaultValue() {
        return defaultValue;
    }

    /** 默认值是否为关键字，是则由 {@link #defaultKeyword()} 给出其名。 */
    public boolean isDefaultKeyword() {
        return defaultKeyword != null;
    }

    /** 默认值命中的关键字名，不是关键字时为 null。 */
    public String defaultKeyword() {
        return defaultKeyword;
    }

    /** 该条目声明的全部关键字，无声明时为空 map。 */
    public Map<String, Keyword<T>> keywords() {
        return keywords;
    }

    /** 下界声明，未声明为 null。 */
    public T min() {
        return min;
    }

    /** 上界声明，未声明为 null。 */
    public T max() {
        return max;
    }

    /** 值域约束，未声明为 null。 */
    public Constraint<T> constraint() {
        return constraint;
    }

    /** 当前值。 */
    public T get() {
        return value;
    }

    /** 由读盘逻辑写入当前值，外部不要调用。 */
    public void set(T v) {
        this.value = v;
    }

    /** 当前值是否为关键字形式。 */
    public boolean isKeyword() {
        return valueKeyword != null;
    }

    /** 当前值命中的关键字名，不是关键字时为 null。 */
    public String keyword() {
        return valueKeyword;
    }

    /** 由读盘逻辑记录当前值命中的关键字名，外部不要调用。 */
    public void setKeyword(String kwd) {
        this.valueKeyword = kwd;
    }
}
