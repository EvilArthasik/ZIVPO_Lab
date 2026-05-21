package com.example.demo.signature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class JsonCanonicalizer {
    private final ObjectMapper objectMapper;

    public JsonCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] canonicalBytes(Object payload) {
        return canonicalize(payload).getBytes(StandardCharsets.UTF_8);
    }

    public String canonicalize(Object payload) {
        if (payload == null) {
            throw new SignatureException("Payload for signature must not be null");
        }
        JsonNode jsonNode = objectMapper.valueToTree(payload);
        StringBuilder builder = new StringBuilder();
        append(jsonNode, builder);
        return builder.toString();
    }

    private void append(JsonNode node, StringBuilder builder) {
        if (node.isObject()) {
            appendObject(node, builder);
        } else if (node.isArray()) {
            appendArray(node, builder);
        } else if (node.isTextual()) {
            appendString(node.textValue(), builder);
        } else if (node.isNumber()) {
            appendNumber(node, builder);
        } else if (node.isBoolean()) {
            builder.append(node.booleanValue());
        } else if (node.isNull()) {
            builder.append("null");
        } else {
            throw new SignatureException("Unsupported JSON node for canonicalization: " + node.getNodeType());
        }
    }

    private void appendObject(JsonNode node, StringBuilder builder) {
        builder.append('{');
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        iterator.forEachRemaining(fields::add);
        fields.sort(Comparator.comparing(Map.Entry::getKey));
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            Map.Entry<String, JsonNode> field = fields.get(i);
            appendString(field.getKey(), builder);
            builder.append(':');
            append(field.getValue(), builder);
        }
        builder.append('}');
    }

    private void appendArray(JsonNode node, StringBuilder builder) {
        builder.append('[');
        for (int i = 0; i < node.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            append(node.get(i), builder);
        }
        builder.append(']');
    }

    private void appendString(String value, StringBuilder builder) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw new SignatureException("JSON string contains a lone high surrogate");
                }
                builder.append(current).append(value.charAt(++i));
            } else if (Character.isLowSurrogate(current)) {
                throw new SignatureException("JSON string contains a lone low surrogate");
            } else {
                appendEscapedCharacter(current, builder);
            }
        }
        builder.append('"');
    }

    private void appendEscapedCharacter(char current, StringBuilder builder) {
        switch (current) {
            case '"' -> builder.append("\\\"");
            case '\\' -> builder.append("\\\\");
            case '\b' -> builder.append("\\b");
            case '\t' -> builder.append("\\t");
            case '\n' -> builder.append("\\n");
            case '\f' -> builder.append("\\f");
            case '\r' -> builder.append("\\r");
            default -> {
                if (current <= 0x1f) {
                    builder.append("\\u");
                    String hex = Integer.toHexString(current);
                    builder.append("0".repeat(4 - hex.length())).append(hex);
                } else {
                    builder.append(current);
                }
            }
        }
    }

    private void appendNumber(JsonNode node, StringBuilder builder) {
        if (node.isFloatingPointNumber() && (!Double.isFinite(node.doubleValue()))) {
            throw new SignatureException("JSON numbers NaN and Infinity are not allowed");
        }
        builder.append(node.asText());
    }
}
