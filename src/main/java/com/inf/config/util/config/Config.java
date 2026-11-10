package com.inf.config.util.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 一份配置文件的条目集合：一个实例持有一份文件与它自己的条目表，逐项用 builder 声明。
 *
 * <p>值的来源在 value 与 default 位置上二选一：显式字面量，或由
 * {@link Builder#keyword(String, Supplier)} 声明的关键字。命中关键字时由该关键字的取值器在读盘期
 * 求值一次。判据是按该条目声明的关键字反查，因此没有声明过的字面量不会被截走。
 *
 * <p>{@link Builder#define(Object)} 是 {@code T} 的重载，字面量默认值因此有静态类型检查；
 * 关键字名形式的默认值走 {@code define(String)}，名字必须是已声明的关键字。方法名首字母必须大写：
 * {@code default} 是 Java 关键字，不能做方法名。
 *
 * <p>值域由 {@code min} 与 {@code max} 声明，只作用于显式写出的值，见 {@link ConfigEntry.Constraint}。
 *
 * <p>工厂方法按值类型各一个，返回对应类型的 builder，九种类型合起来就是本类支持的值域。
 *
 * <p>一次性语义：{@link #init()} 是每个实例唯一的读盘点，读一次、解析一次、约束一次。
 * 本类不提供重载入口，原因是调用方通常把值缓存在自己的字段里，重载会产出一部分生效、
 * 一部分永远不生效的状态，而那条分界线既不在文件里也不在日志里。
 */
public final class Config {

    /** 该实例的文件路径，由 filePath 经 {@link #resolve(String)} 解析，见构造器。 */
    private final Path file;

    /** 该文件顶层的 note，落盘时写进根对象的 note 键。 */
    private final Map<String, String> fileNotes;

    /** 本文件的条目表，保声明顺序。 */
    private final Map<String, ConfigEntry<?>> entries = new LinkedHashMap<>();

    /** 幂等标志：init() 只读一次本实例的文件；持有它的类重复调用无害。 */
    private boolean initialized;

    /**
     * 文件路径相对 {@code ${user.dir}/config} 解释，永远追加 {@code .json}。
     *
     * <p>
     * 空串、绝对路径、归一化后越出 config 目录，都在这里抛：越出的路径若拖到写盘期，
     * {@code Files.createDirectories} 会在 config 之外建目录。
     */
    public Config(String filePath, Map<String, String> fileNotes) {
        this.file = resolve(filePath);
        this.fileNotes = Map.copyOf(fileNotes);
    }

    /** 解析 filePath 并夹在 config 目录内，判据见构造器。包私有，供同包测试直接调用。 */
    static Path resolve(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("config: path must not be blank");
        }
        Path raw = Path.of(filePath);
        if (raw.isAbsolute() || raw.getRoot() != null) {
            throw new IllegalArgumentException("config: path must be relative: " + filePath);
        }
        Path base = Path.of(System.getProperty("user.dir"), "config").normalize();
        Path resolved = base.resolve(filePath + ".json").normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException(
                    "config: path escapes the config directory: " + filePath + " -> " + resolved);
        }
        return resolved;
    }

    // ---- 工厂：9 种值类型 ----

    public IntBuilder setInt(String name) {
        return new IntBuilder(this, name);
    }

    public LongBuilder setLong(String name) {
        return new LongBuilder(this, name);
    }

    public ShortBuilder setShort(String name) {
        return new ShortBuilder(this, name);
    }

    public ByteBuilder setByte(String name) {
        return new ByteBuilder(this, name);
    }

    public FloatBuilder setFloat(String name) {
        return new FloatBuilder(this, name);
    }

    public DoubleBuilder setDouble(String name) {
        return new DoubleBuilder(this, name);
    }

    public BoolBuilder setBoolean(String name) {
        return new BoolBuilder(this, name);
    }

    /** 类名不叫 StringBuilder：那会遮蔽 {@link java.lang.StringBuilder}。 */
    public StringsBuilder setString(String name) {
        return new StringsBuilder(this, name);
    }

    public <T extends Enum<T>> EnumBuilder<T> setEnum(String name, Class<T> type) {
        return new EnumBuilder<>(this, name, type);
    }

    // ---- builder ----

    /**
     * 配置项 builder 的公共基类：注释、关键字、默认值、值域与约束的声明入口。
     *
     * <p>子类各按一种值类型实现 {@link #type()}，并让 {@link #self()} 返回自身，链式声明因此
     * 保持具体类型。
     *
     * @param <T> 值类型
     * @param <B> 具体 builder 类型
     */
    public abstract static class Builder<T, B extends Builder<T, B>> {

        /** 产出本 builder 的配置实例，build() 把条目登记进它的表。 */
        private final Config owner;

        protected final String name;
        protected final Map<String, String> notes = new LinkedHashMap<>();
        protected T defaultValue;
        protected final Map<String, ConfigEntry.Keyword<T>> keywords = new LinkedHashMap<>();
        /** 默认值命中关键字时记其名字，未命中为 null。 */
        protected String defaultKeyword;
        /** 值域边界各自独立，可只声明一侧。非数值项恒为 null。 */
        protected T min;
        protected T max;
        protected ConfigEntry.Constraint<T> constraint;

        Builder(Config owner, String name) {
            if (!ConfigEntry.isValidName(name)) {
                throw new IllegalArgumentException("Invalid config entry name: %s".formatted(name));
            }
            this.owner = owner;
            this.name = name;
        }

        /** 返回具体 builder 自身，链式声明据此保持类型。 */
        public abstract B self();

        /** 该 builder 产出的值类型，枚举项为枚举类本身。 */
        protected abstract Class<?> type();

        /** 追加一条注释，来源键到文本，落进条目自己的 {@code note}。 */
        public B comment(String source, String text) {
            notes.put(source, text);
            return self();
        }

        /**
         * 声明一个关键字及其取值器。关键字名不得重复。取值器在读盘期命中该名字时求值一次。
         *
         * <p>
         * 它的注释用 {@link #commentKeyword(String, String, String)} 追加，与
         * {@link #comment(String, String)} 同形。两者分开，是因为条目自己的注释与某个关键字的注释
         * 是两处不同的落点。
         */
        public final B keyword(String kwd, Supplier<T> supplier) {
            if (keywords.containsKey(kwd)) {
                throw new IllegalArgumentException(
                        "Config entry '%s': duplicate keyword '%s'".formatted(name, kwd));
            }
            keywords.put(kwd, new ConfigEntry.Keyword<>(supplier, new LinkedHashMap<>()));
            return self();
        }

        /**
         * 给指定关键字追加一条注释，落在 {@code allow.keyword} 该元素的 {@code note} 上。
         * 与 {@link #comment(String, String)} 同形，只是落点不同；按名字定位，不依赖声明顺序。
         */
        public B commentKeyword(String kwd, String source, String text) {
            ConfigEntry.Keyword<T> kw = keywords.get(kwd);
            if (kw == null) {
                throw new IllegalArgumentException(
                        "Config entry '%s': commentKeyword references undeclared keyword '%s'".formatted(name, kwd));
            }
            kw.notes().put(source, text);
            return self();
        }

        /**
         * 默认值。字面量形式，{@code T} 由各 builder 的类型参数给出，因此取值有静态类型检查。
         *
         * <p>
         * 不做 {@code define(String)} 的重载：{@code T} 为 {@code String} 时两者擦除后签名
         * 相同，编译期就冲突。String 项改由覆写本方法承担。
         */
        public B define(T value) {
            if (value instanceof String token) {
                ConfigEntry.Keyword<T> kw = keywords.get(token);
                if (kw != null) {
                    this.defaultValue = kw.supplier().get();
                    this.defaultKeyword = token;
                    return self();
                }
                throw new IllegalArgumentException(
                        "Config entry '%s': define(\"%s\") is neither a declared keyword nor a literal of this type"
                                .formatted(name, token));
            }
            this.defaultValue = value;
            this.defaultKeyword = null;
            return self();
        }

        /**
         * 收尾：校验声明完整性，把条目登记进所属文件的条目表并返回。
         *
         * <p>重名与缺 {@code define} 都在这里抛。默认值自身也要过一遍值域约束，否则新生成的那份
         * 文件一开始就是坏的；关键字默认值不校验，它由代码算出。
         */
        public ConfigEntry<T> build() {
            if (owner.entries.containsKey(name)) {
                throw new IllegalStateException("Duplicate config entry: %s".formatted(name));
            }
            if (defaultValue == null && defaultKeyword == null) {
                throw new IllegalStateException("Config entry '%s': missing define(...)".formatted(name));
            }
            if (defaultKeyword == null && constraint != null) {
                // 默认值自身也要合法，否则一份新生成的配置就是坏的。
                // 关键字默认值不校验：它由代码算出，不是用户输入。
                constraint.check(defaultValue);
            }
            ConfigEntry<T> e = new ConfigEntry<>(name, notes, type(), defaultValue, defaultKeyword,
                    keywords, min, max, constraint);
            owner.entries.put(name, e);
            return e;
        }
    }

    /**
     * 数值类型的公共基类。边界方法只在这里出现，非数值类型没有值域，也就没有这些方法。
     * 类型参数取 {@code T extends Number & Comparable<T>} 而不是 {@code Comparable<?>}：
     * 后者在共享基类里比较两个通配符参数会撞 wildcard capture。
     *
     * <p>两侧各自独立，可只声明一侧，也就只校验那一侧。{@code range} 是一次声明两侧的糖。
     */
    public abstract static class NumericBuilder<T extends Number & Comparable<T>, B extends NumericBuilder<T, B>>
            extends Builder<T, B> {

        NumericBuilder(Config owner, String name) {
            super(owner, name);
        }

        /** 下界。与 {@link #max} 无关，可单独声明。 */
        public B min(T value) {
            checkBoundary(value);
            this.min = value;
            this.constraint = mergeConstraint(this.constraint, added -> {
                if (added.compareTo(value) < 0) {
                    throw new IllegalArgumentException("value %s below min %s".formatted(added, value));
                }
            });
            return self();
        }

        /** 上界。与 {@link #min} 无关，可单独声明。 */
        public B max(T value) {
            checkBoundary(value);
            this.max = value;
            this.constraint = mergeConstraint(this.constraint, added -> {
                if (added.compareTo(value) > 0) {
                    throw new IllegalArgumentException("value %s above max %s".formatted(added, value));
                }
            });
            return self();
        }

        /** 一次声明两侧，等价于 {@code min(from).max(to)}。 */
        public B range(T from, T to) {
            checkBoundary(from);
            checkBoundary(to);
            if (from.compareTo(to) > 0) {
                throw new IllegalArgumentException(
                        "Config entry '%s': range min > max (%s > %s)".formatted(name, from, to));
            }
            return min(from).max(to);
        }

        /**
         * 关键字名形式的默认值。这里而不是基类声明：基类的 {@code define(T)} 在 {@code T} 为
         * {@code String} 时与本方法擦除后签名相同，放基类编译不过。关键字项的取值类型都是数值，
         * 因此只需要这一个子类承载它。
         */
        public B define(String token) {
            ConfigEntry.Keyword<T> kw = keywords.get(token);
            if (kw == null) {
                throw new IllegalArgumentException(
                        "Config entry '%s': define(\"%s\") is not a declared keyword".formatted(name, token));
            }
            this.defaultValue = kw.supplier().get();
            this.defaultKeyword = token;
            return self();
        }

        /**
         * 复合约束。每个边界各自一条，因此 {@link #range} 可以走
         * {@link #min} 与 {@link #max}：两条判定串联，不存在相互覆盖。
         */
        private ConfigEntry.Constraint<T> mergeConstraint(ConfigEntry.Constraint<T> base,
                ConfigEntry.Constraint<T> added) {
            if (base == null) {
                return added;
            }
            return value -> {
                base.check(value);
                added.check(value);
            };
        }

        /**
         * 边界值不得为 NaN。{@code compareTo} 对 NaN 恒返回负值，会让边界静默失效：
         * {@code value < NaN} 为假、{@code value > NaN} 为假。Infinity 不受影响，属合法边界。
         */
        private void checkBoundary(T value) {
            if (value instanceof Double d && d.isNaN() || value instanceof Float f && f.isNaN()) {
                throw new IllegalArgumentException("Config entry '%s': boundary is NaN".formatted(name));
            }
        }
    }

    public static final class IntBuilder extends NumericBuilder<Integer, IntBuilder> {
        IntBuilder(Config owner, String name) {
            super(owner, name);
        }

        @Override
        public IntBuilder self() {
            return this;
        }

        @Override
        protected Class<?> type() {
            return int.class;
        }
    }

    public static final class LongBuilder extends NumericBuilder<Long, LongBuilder> {
        LongBuilder(Config owner, String name) {
            super(owner, name);
        }

        @Override
        public LongBuilder self() {
            return this;
        }

        @Override
        protected Class<?> type() {
            return long.class;
        }
    }

    public static final class ShortBuilder extends NumericBuilder<Short, ShortBuilder> {
        ShortBuilder(Config owner, String name) {
            super(owner, name);
        }

        @Override
        public ShortBuilder self() {
            return this;
        }

        @Override
        protected Class<?> type() {
            return short.class;
        }
    }

    public static final class ByteBuilder extends NumericBuilder<Byte, ByteBuilder> {
        ByteBuilder(Config owner, String name) {
            super(owner, name);
        }

        @Override
        public ByteBuilder self() {
            return this;
        }

        @Override
        protected Class<?> type() {
            return byte.class;
        }
    }

    public static final class FloatBuilder extends NumericBuilder<Float, FloatBuilder> {
        FloatBuilder(Config owner, String name) {
            super(owner, name);
        }

        @Override
        public FloatBuilder self() {
            return this;
        }

        @Override
        protected Class<?> type() {
            return float.class;
        }
    }

    public static final class DoubleBuilder extends NumericBuilder<Double, DoubleBuilder> {
        DoubleBuilder(Config owner, String name) {
            super(owner, name);
        }

        @Override
        public DoubleBuilder self() {
            return this;
        }

        @Override
        protected Class<?> type() {
            return double.class;
        }
    }

    public static final class BoolBuilder extends Builder<Boolean, BoolBuilder> {
        BoolBuilder(Config owner, String name) {
            super(owner, name);
        }

        @Override
        public BoolBuilder self() {
            return this;
        }

        @Override
        protected Class<?> type() {
            return boolean.class;
        }

        /** 关键字名形式的默认值，理由同 {@link NumericBuilder#define(String)}。 */
        public BoolBuilder define(String token) {
            ConfigEntry.Keyword<Boolean> kw = keywords.get(token);
            if (kw == null) {
                throw new IllegalArgumentException(
                        "Config entry '%s': define(\"%s\") is not a declared keyword".formatted(name, token));
            }
            this.defaultValue = kw.supplier().get();
            this.defaultKeyword = token;
            return this;
        }
    }

    public static final class StringsBuilder extends Builder<String, StringsBuilder> {
        StringsBuilder(Config owner, String name) {
            super(owner, name);
        }

        @Override
        public StringsBuilder self() {
            return this;
        }

        @Override
        protected Class<?> type() {
            return String.class;
        }

        /** String 项：字符串既可以是已声明的关键字，也可以是字面量本身。 */
        public StringsBuilder define(String value) {
            ConfigEntry.Keyword<String> kw = keywords.get(value);
            if (kw != null) {
                this.defaultValue = kw.supplier().get();
                this.defaultKeyword = value;
                return this;
            }
            this.defaultValue = value;
            this.defaultKeyword = null;
            return this;
        }
    }

    public static final class EnumBuilder<T extends Enum<T>> extends Builder<T, EnumBuilder<T>> {

        private final Class<T> enumType;

        EnumBuilder(Config owner, String name, Class<T> type) {
            super(owner, name);
            this.enumType = type;
        }

        @Override
        public EnumBuilder<T> self() {
            return this;
        }

        @Override
        protected Class<?> type() {
            return enumType;
        }
    }

    // ---- 读盘 ----

    /**
     * 读本实例的文件。幂等：首次调用读磁盘并填充条目，之后直接返回。
     *
     * <p>调用点通常放在持有本实例的类的静态块里，由第一个引用它的类触发。方法之后没有任何重新
     * 读盘的入口，重载不可行，理由见类注释。
     */
    public void init() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        ConfigFile.load(this.file, this.fileNotes, this.entries);
    }

}
