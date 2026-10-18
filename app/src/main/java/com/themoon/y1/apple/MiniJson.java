package com.themoon.y1.apple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON reader for the Apple Music library payload. Objects keep insertion order. */
public final class MiniJson {
    private final String text;
    private int index;

    private MiniJson(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("Missing JSON");
        MiniJson parser = new MiniJson(text);
        Object value = parser.readValue();
        parser.skipSpace();
        if (parser.index != text.length()) throw new IllegalArgumentException("Trailing JSON");
        return value;
    }

    private Object readValue() {
        skipSpace();
        if (index >= text.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        char c = text.charAt(index);
        if (c == '{') return readObject();
        if (c == '[') return readArray();
        if (c == '"') return readString();
        if (c == 't' || c == 'f') return readLiteral();
        if (c == 'n') return readNull();
        if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
        throw new IllegalArgumentException("Unexpected JSON at " + index);
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> object = new LinkedHashMap<>();
        skipSpace();
        if (peek('}')) {
            index++;
            return object;
        }
        while (true) {
            skipSpace();
            String key = readString();
            skipSpace();
            expect(':');
            object.put(key, readValue());
            skipSpace();
            if (peek('}')) {
                index++;
                return object;
            }
            expect(',');
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipSpace();
        if (peek(']')) {
            index++;
            return list;
        }
        while (true) {
            list.add(readValue());
            skipSpace();
            if (peek(']')) {
                index++;
                return list;
            }
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (index < text.length()) {
            char c = text.charAt(index++);
            if (c == '"') return out.toString();
            if (c == '\\') {
                if (index >= text.length()) break;
                char esc = text.charAt(index++);
                switch (esc) {
                    case '"':
                    case '\\':
                    case '/':
                        out.append(esc);
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (index + 4 > text.length()) throw new IllegalArgumentException("Bad unicode escape");
                        int code = Integer.parseInt(text.substring(index, index + 4), 16);
                        index += 4;
                        out.append((char) code);
                        break;
                    default:
                        throw new IllegalArgumentException("Bad escape");
                }
            } else {
                out.append(c);
            }
        }
        throw new IllegalArgumentException("Unclosed string");
    }

    private Object readLiteral() {
        if (text.startsWith("true", index)) {
            index += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", index)) {
            index += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Bad literal");
    }

    private Object readNull() {
        if (text.startsWith("null", index)) {
            index += 4;
            return null;
        }
        throw new IllegalArgumentException("Bad null");
    }

    private Number readNumber() {
        int start = index;
        if (peek('-')) index++;
        while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') index++;
        if (index < text.length() && text.charAt(index) == '.') {
            index++;
            while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') index++;
        }
        String raw = text.substring(start, index);
        if (raw.indexOf('.') >= 0) return Double.valueOf(raw);
        return Long.valueOf(raw);
    }

    private void skipSpace() {
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') return;
            index++;
        }
    }

    private boolean peek(char c) {
        return index < text.length() && text.charAt(index) == c;
    }

    private void expect(char c) {
        skipSpace();
        if (!peek(c)) throw new IllegalArgumentException("Expected '" + c + "'");
        index++;
    }
}
