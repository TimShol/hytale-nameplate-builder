package com.frotty27.nameplatebuilder.server;

import java.util.*;

final class SimpleJson {

    private SimpleJson() {}

    static final class Writer {
        private final StringBuilder stringBuilder = new StringBuilder();
        private int indent = 0;
        private boolean needsComma = false;

        Writer beginObject() {
            appendCommaIfNeeded();
            stringBuilder.append("{\n");
            indent++;
            needsComma = false;
            return this;
        }

        Writer endObject() {
            stringBuilder.append('\n');
            indent--;
            indent();
            stringBuilder.append('}');
            needsComma = true;
            return this;
        }

        Writer beginArray() {
            appendCommaIfNeeded();
            stringBuilder.append("[\n");
            indent++;
            needsComma = false;
            return this;
        }

        Writer endArray() {
            stringBuilder.append('\n');
            indent--;
            indent();
            stringBuilder.append(']');
            needsComma = true;
            return this;
        }

        Writer key(String key) {
            appendCommaIfNeeded();
            indent();
            stringBuilder.append('"').append(escape(key)).append("\": ");
            needsComma = false;
            return this;
        }

        Writer value(String value) {
            appendCommaIfNeeded();
            if (value == null) {
                stringBuilder.append("null");
            } else {
                stringBuilder.append('"').append(escape(value)).append('"');
            }
            needsComma = true;
            return this;
        }

        Writer value(boolean value) {
            appendCommaIfNeeded();
            stringBuilder.append(value);
            needsComma = true;
            return this;
        }

        Writer value(int value) {
            appendCommaIfNeeded();
            stringBuilder.append(value);
            needsComma = true;
            return this;
        }

        Writer value(double value) {
            appendCommaIfNeeded();
            if (value == (long) value) {
                stringBuilder.append((long) value).append(".0");
            } else {
                stringBuilder.append(value);
            }
            needsComma = true;
            return this;
        }

        Writer rawValue() {
            return this;
        }

        Writer keyValue(String key, String value) {
            key(key);
            value(value);
            return this;
        }

        Writer keyValue(String key, boolean value) {
            key(key);
            value(value);
            return this;
        }

        Writer keyValue(String key, int value) {
            key(key);
            value(value);
            return this;
        }

        Writer keyValue(String key, double value) {
            key(key);
            value(value);
            return this;
        }

        Writer keyBooleanMap(String key, Map<String, Boolean> map) {
            key(key);
            beginObject();
            for (Map.Entry<String, Boolean> entry : map.entrySet()) {
                keyValue(entry.getKey(), entry.getValue());
            }
            endObject();
            return this;
        }

        Writer keyStringMap(String key, Map<String, String> map) {
            key(key);
            beginObject();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                keyValue(entry.getKey(), entry.getValue());
            }
            endObject();
            return this;
        }

        Writer keyIntMap(String key, Map<String, Integer> map) {
            key(key);
            beginObject();
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                keyValue(entry.getKey(), entry.getValue());
            }
            endObject();
            return this;
        }

        Writer keyStringArray(String key, List<String> list) {
            key(key);
            beginArray();
            for (String s : list) {
                value(s);
            }
            endArray();
            return this;
        }

        Writer keyStringArray(String key, Set<String> set) {
            key(key);
            beginArray();
            for (String s : set) {
                value(s);
            }
            endArray();
            return this;
        }

        @Override
        public String toString() {
            return stringBuilder.toString();
        }

        private void appendCommaIfNeeded() {
            if (needsComma) {
                stringBuilder.append(",\n");
                needsComma = false;
            }
        }

        private void indent() {
            for (int i = 0; i < indent; i++) {
                stringBuilder.append("  ");
            }
        }

        private static String escape(String s) {
            if (s == null) return "null";
            StringBuilder escaped = new StringBuilder(s.length());
            for (int charIndex = 0; charIndex < s.length(); charIndex++) {
                char character = s.charAt(charIndex);
                switch (character) {
                    case '"' -> escaped.append("\\\"");
                    case '\\' -> escaped.append("\\\\");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            escaped.append(String.format("\\u%04x", (int) character));
                        } else {
                            escaped.append(character);
                        }
                    }
                }
            }
            return escaped.toString();
        }
    }

    static Object parse(String json) {
        if (json == null || json.isBlank()) return null;
        Reader r = new Reader(json.trim());
        return r.readValue();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String json) {
        Object result = parse(json);
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value instanceof String s) return s;
        return defaultValue;
    }

    static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean b) return b;
        return defaultValue;
    }

    static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        return defaultValue;
    }

    static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> getObject(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> getArray(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> l) return (List<Object>) l;
        return null;
    }

    static List<String> getStringList(Map<String, Object> map, String key) {
        List<Object> arr = getArray(map, key);
        if (arr == null) return List.of();
        List<String> result = new ArrayList<>(arr.size());
        for (Object o : arr) {
            if (o instanceof String s) result.add(s);
        }
        return result;
    }

    static Map<String, Boolean> getBooleanMap(Map<String, Object> map, String key) {
        Map<String, Object> obj = getObject(map, key);
        if (obj == null) return Map.of();
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            if (e.getValue() instanceof Boolean b) {
                result.put(e.getKey(), b);
            }
        }
        return result;
    }

    static Map<String, String> getStringMap(Map<String, Object> map, String key) {
        Map<String, Object> obj = getObject(map, key);
        if (obj == null) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            if (e.getValue() instanceof String s) {
                result.put(e.getKey(), s);
            }
        }
        return result;
    }

    static Map<String, Integer> getIntMap(Map<String, Object> map, String key) {
        Map<String, Object> obj = getObject(map, key);
        if (obj == null) return Map.of();
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            if (e.getValue() instanceof Number n) {
                result.put(e.getKey(), n.intValue());
            }
        }
        return result;
    }

    private static final class Reader {
        private final String source;
        private int position;

        Reader(String source) {
            this.source = source;
            this.position = 0;
        }

        Object readValue() {
            skipWhitespace();
            if (position >= source.length()) return null;
            char character = source.charAt(position);
            return switch (character) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == '}') {
                position++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                if (position >= source.length()) break;
                if (source.charAt(position) == ',') {
                    position++;
                } else {
                    break;
                }
            }
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == '}') position++;
            return map;
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == ']') {
                position++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                if (position >= source.length()) break;
                if (source.charAt(position) == ',') {
                    position++;
                } else {
                    break;
                }
            }
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == ']') position++;
            return list;
        }

        private String readString() {
            skipWhitespace();
            expect('"');
            StringBuilder stringBuilder = new StringBuilder();
            while (position < source.length()) {
                char character = source.charAt(position);
                if (character == '"') {
                    position++;
                    return stringBuilder.toString();
                }
                if (character == '\\') {
                    position++;
                    if (position >= source.length()) break;
                    char escapedCharacter = source.charAt(position);
                    switch (escapedCharacter) {
                        case '"' -> stringBuilder.append('"');
                        case '\\' -> stringBuilder.append('\\');
                        case '/' -> stringBuilder.append('/');
                        case 'n' -> stringBuilder.append('\n');
                        case 'r' -> stringBuilder.append('\r');
                        case 't' -> stringBuilder.append('\t');
                        case 'u' -> {
                            if (position + 4 < source.length()) {
                                String hex = source.substring(position + 1, position + 5);
                                stringBuilder.append((char) Integer.parseInt(hex, 16));
                                position += 4;
                            }
                        }
                        default -> stringBuilder.append(escapedCharacter);
                    }
                } else {
                    stringBuilder.append(character);
                }
                position++;
            }
            return stringBuilder.toString();
        }

        private Boolean readBoolean() {
            if (source.startsWith("true", position)) {
                position += 4;
                return Boolean.TRUE;
            } else if (source.startsWith("false", position)) {
                position += 5;
                return Boolean.FALSE;
            }
            throw new IllegalStateException("Expected boolean at position " + position);
        }

        private Object readNull() {
            if (source.startsWith("null", position)) {
                position += 4;
                return null;
            }
            throw new IllegalStateException("Expected null at position " + position);
        }

        private Number readNumber() {
            int start = position;
            if (position < source.length() && source.charAt(position) == '-') position++;
            while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            boolean isDouble = false;
            if (position < source.length() && source.charAt(position) == '.') {
                isDouble = true;
                position++;
                while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            }
            if (position < source.length() && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
                isDouble = true;
                position++;
                if (position < source.length() && (source.charAt(position) == '+' || source.charAt(position) == '-')) position++;
                while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            }
            String numberString = source.substring(start, position);
            if (isDouble) {
                return Double.parseDouble(numberString);
            }
            long parsedValue = Long.parseLong(numberString);
            if (parsedValue >= Integer.MIN_VALUE && parsedValue <= Integer.MAX_VALUE) {
                return (int) parsedValue;
            }
            return parsedValue;
        }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private void expect(char character) {
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == character) {
                position++;
            }
        }
    }
}
