package org.xnio;

import java.util.HashMap;
import java.util.Map;

public abstract class Option<T> {
    protected interface ValueParser<T> {
        T parseValue(String string, ClassLoader classLoader) throws IllegalArgumentException;
    }

    static final Map<Class<?>, ValueParser<?>> parsers;

    static final Option.ValueParser<?> noParser = new Option.ValueParser<Object>() {
        public Object parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
            throw new IllegalArgumentException("No parser for this option value type");
        }
    };

    static {
        final Map<Class<?>, Option.ValueParser<?>> map = new HashMap<Class<?>, ValueParser<?>>();
        map.put(Byte.class, new Option.ValueParser<Byte>() {
            public Byte parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return Byte.decode(string.trim());
            }
        });
        map.put(Short.class, new Option.ValueParser<Short>() {
            public Short parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return Short.decode(string.trim());
            }
        });
        map.put(Integer.class, new Option.ValueParser<Integer>() {
            public Integer parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return Integer.decode(string.trim());
            }
        });
        map.put(Long.class, new Option.ValueParser<Long>() {
            public Long parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return Long.decode(string.trim());
            }
        });
        map.put(String.class, new Option.ValueParser<String>() {
            public String parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return string.trim();
            }
        });
        map.put(Boolean.class, new Option.ValueParser<Boolean>() {
            public Boolean parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return Boolean.valueOf(string.trim());
            }
        });
//        map.put(Property.class, new Option.ValueParser<Object>() {
//            public Object parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
//                final int idx = string.indexOf('=');
//                if (idx == -1) {
//                    throw msg.invalidOptionPropertyFormat(string);
//                }
//                return Property.of(string.substring(0, idx), string.substring(idx + 1, string.length()));
//            }
//        });
        parsers = map;
    }

    static <T> Option.ValueParser<Class<? extends T>> getClassParser(final Class<T> argType) {
        return new ValueParser<Class<? extends T>>() {
            public Class<? extends T> parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                try {
                    return Class.forName(string, false, classLoader).asSubclass(argType);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(String.format("Class %s not found", argType.getName()), e);
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException(String.format("Class %s is not an instance of %s", string, argType));
                }
            }
        };
    }

    static <T, E extends Enum<E>> Option.ValueParser<T> getEnumParser(final Class<T> enumType) {
        return new ValueParser<T>() {
            public T parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return enumType.cast(Enum.<E>valueOf(asEnum(enumType), string.trim()));
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Enum<E>> Class<E> asEnum(final Class<T> enumType) {
        return (Class<E>) enumType;
    }

    @SuppressWarnings("unchecked")
    public static <T> Option.ValueParser<T> getParser(final Class<T> argType) {
        if (argType.isEnum()) {
            return getEnumParser(argType);
        } else {
            final Option.ValueParser<?> value = parsers.get(argType);
            return (Option.ValueParser<T>) (value == null ? noParser : value);
        }
    }

    public abstract T parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException;
}
