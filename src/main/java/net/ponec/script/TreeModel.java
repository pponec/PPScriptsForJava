package net.ponec.script;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * TreeModel represents a simple hierarchical data structure used for
 * converting between PROPERTIES and YAML formats.
 *
 * <p>The model stores data as a tree where all leaf nodes are {@code String} values.
 * In the PROPERTIES format, nested keys are represented using dot notation
 * (e.g., {@code user.address.city}), while in YAML they are represented using indentation.</p>
 *
 * <p>This class does not rely on any third-party libraries and uses only standard Java APIs.
 * It supports only basic key-value structures without sequences or complex YAML features.</p>
 *
 * <p>The static {@code CHARSET} field defines the character encoding used when parsing
 * or exporting PROPERTIES content.</p>
 *
 * <p>Main functionality includes:</p>
 * <ul>
 *   <li>Parsing from PROPERTIES or YAML string inputs into the internal tree model</li>
 *   <li>Exporting the model back into PROPERTIES or YAML string formats</li>
 *   <li>Retrieving values by fully qualified keys, with support for default values</li>
 * </ul>
 */
public class TreeModel {

    public static Charset CHARSET = StandardCharsets.UTF_8;

    private final Map<String, Object> root = new HashMap<>();

    /**
     * Parses a PROPERTIES formatted string and returns a TreeModel instance.
     */
    public static TreeModel parseProps(String propsText) {
        var result = new TreeModel();
        var props = new Properties();
        try {
            props.load(new ByteArrayInputStream(propsText.getBytes(CHARSET)));
            for (var key : props.stringPropertyNames()) {
                result.setValue(key, props.getProperty(key));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid properties format", e);
        }
        return result;
    }

    /**
     * Parses a simple YAML formatted string and returns a TreeModel instance.
     */
    public static TreeModel parseYaml(String yamlText) {
        var result = new TreeModel();
        var lines = yamlText.split("\\r?\\n");
        var keyStack = new ArrayDeque<String>();
        var prevIndent = 0;

        for (var line : lines) {
            if (line.trim().isEmpty()) continue;
            var indent = countLeadingSpaces(line);
            var trimmedLine = line.trim();

            var parts = trimmedLine.split(":", 2);
            if (parts.length < 2) continue;

            var key = parts[0].trim();
            var value = parts[1].trim();

            while (indent < prevIndent && !keyStack.isEmpty()) {
                keyStack.pop();
                prevIndent -= 2;
            }

            if (!value.isEmpty()) {
                var fullKey = String.join(".", keyStack) + (keyStack.isEmpty() ? "" : ".") + key;
                result.setValue(fullKey, value);
            } else {
                keyStack.push(key);
                prevIndent = indent;
            }
        }

        return result;
    }

    /**
     * Converts the tree model to a PROPERTIES formatted string.
     */
    public String toProps() {
        var result = new Properties();
        buildProps("", root, result);
        var out = new java.io.ByteArrayOutputStream();
        try {
            result.store(out, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize properties", e);
        }
        return out.toString(CHARSET);
    }

    /**
     * Converts the tree model to a simple YAML formatted string.
     */
    public String toYaml() {
        var result = new StringBuilder();
        buildYaml(result, root, 0);
        return result.toString();
    }

    /**
     * Gets a value from the model by full key.
     */
    public String getValue(String key) {
        return getValue(key, null);
    }

    /**
     * Gets a value from the model by full key.
     */
    public String getValue(String key, String defaultValue) {
        var parts = key.split("\\.");
        Object current = root;
        for (var part : parts) {
            if (!(current instanceof Map)) return defaultValue;
            current = ((Map<?, ?>) current).get(part);
        }
        return current instanceof String ? (String) current : defaultValue;
    }

    // --- Private Helpers ---

    private void setValue(String key, String value) {
        var parts = key.split("\\.");
        var current = root;
        for (var i = 0; i < parts.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new HashMap<>());
        }
        current.put(parts[parts.length - 1], value);
    }

    private void buildProps(String prefix, Object node, Properties props) {
        if (node instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                var newPrefix = prefix.isEmpty() ? (String) entry.getKey() : prefix + "." + entry.getKey();
                buildProps(newPrefix, entry.getValue(), props);
            }
        } else if (node instanceof String str) {
            props.setProperty(prefix, str);
        }
    }

    private void buildYaml(StringBuilder sb, Object node, int indent) {
        if (node instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                indent(sb, indent).append(entry.getKey()).append(":");
                if (entry.getValue() instanceof String) {
                    sb.append(" ").append(entry.getValue()).append("\n");
                } else {
                    sb.append("\n");
                    buildYaml(sb, entry.getValue(), indent + 2);
                }
            }
        }
    }

    private StringBuilder indent(StringBuilder sb, int spaces) {
        return sb.append(" ".repeat(Math.max(0, spaces)));
    }

    public static int countLeadingSpaces(String line) {
        var count = 0;
        while (count < line.length() && line.charAt(count) == ' ') count++;
        return count;
    }

    /** Print the YAML formát */
    public String toString() {
        return toYaml();
    }
}
