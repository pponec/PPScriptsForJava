package net.ponec.script;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TreeModel represents a simple hierarchical data structure used for
 * converting between PROPERTIES and simplified YAML formats.
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
 * See <a href="https://github.com/pponec/PPScriptsForJava">original</> project.
 *
 * @version 2025-04-10
 * @author https://github.com/pponec
 */
public class TreeModel {

    private final Map<String, Object> root = new TreeMap<>();

    /**
     * Parses a PROPERTIES formatted string and returns a TreeModel instance.
     */
    public static TreeModel ofProps(String propsText) {
        var result = new TreeModel();
        collectMap(propsText).forEach(result::setValue);
        return result;
    }

    private static Map<String, String> collectMap(String propsText) {
        return propsText.lines()
                .filter(line -> !line.trim().isEmpty())       // Filter out empty lines
                .filter(line -> !line.trim().startsWith("#")) // Filter out comments
                .map(line -> line.split("=", 2)) // Split each line by "="
                .filter(parts -> parts.length == 2) // Ensure there are exactly two parts (key, value)
                .collect(Collectors.toMap(
                        parts -> parts[0].trim(),  // Key
                        parts -> parts[1].trim(),  // Value
                        (existing, replacement) -> replacement)); // Use the last value
    }

    /**
     * Parses a simple YAML formatted string and returns a TreeModel instance.
     */
    public static TreeModel ofYaml(String yamlText) {
        var result = new TreeModel();
        var keyStack = new ArrayDeque<String>();
        var indentStack = new ArrayDeque<Integer>();

        yamlText.lines()
                .filter(line -> !line.trim().isEmpty())
                .filter(line -> !line.trim().startsWith("#"))
                .forEach(line -> {

            var indent = countLeadingSpaces(line);
            var trimmedLine = line.trim();
            var parts = trimmedLine.split(":", 2);
            if (parts.length < 2) return;

            var key = parts[0].trim();
            var value = parts[1].trim().replaceAll("^'(.*)'$", "$1"); // No match, return original.
            // Step back to the correct level
            while (!indentStack.isEmpty() && indent <= indentStack.peekLast()) {
                indentStack.removeLast();
                keyStack.removeLast();
            }

            if (!value.isEmpty()) {
                var fullKeyParts = new ArrayList<>(keyStack);
                fullKeyParts.add(key);
                result.setValue(String.join(".", fullKeyParts), value);
            } else {
                keyStack.addLast(key);
                indentStack.addLast(indent);
            }
        });

        return result;
    }

    /**
     * Converts the tree model to a PROPERTIES formatted string.
     */
    public String toProps() {
        final var result = new StringBuilder();
        buildProps("", root, result); // Recur for all tree nodes and build the result
        return result.toString();
    }

    /**
     * Converts the tree model to a simple YAML formatted string.
     */
    public Map<String,String> toMap() {
        return new TreeMap<>(collectMap(toProps()));
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

    public void setValue(String key, String value) {
        var parts = key.split("\\.");
        var current = root;
        for (var i = 0; i < parts.length - 1; i++) {
            try {
                current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new TreeMap<>());
            } catch (ClassCastException ex) {
                throw new IllegalArgumentException("Duplicated YAML key: '%s'".formatted(key), ex);
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    public void setValues(Map<String, String> values) {
        values.forEach(this::setValue);
    }

    // --- Private Helpers ---

    private void buildProps(String prefix, Object node, StringBuilder props) {
        if (node instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                var newPrefix = prefix.isEmpty() ? (String) entry.getKey() : prefix + "." + entry.getKey();
                buildProps(newPrefix, entry.getValue(), props);
            }
        } else if (node instanceof String value) {
            props.append(prefix).append(" = ").append(value).append("\n");
        }
    }

    private void buildYaml(StringBuilder sb, Object node, int indent) {
        if (node instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                indent(sb, indent).append(entry.getKey()).append(":");
                if (entry.getValue() instanceof String value) {
                    sb.append(" ").append(toYamlValue(value)).append("\n");
                } else {
                    sb.append("\n");
                    buildYaml(sb, entry.getValue(), indent + 2);
                }
            }
        }
    }

    /** Enclose the text with a pair of apostrophes. */
    private String toYamlValue(final String value) {
        return value.matches(".*[\\s:].*")
                ? "'" + value + "'"
                : value;
    }

    private StringBuilder indent(StringBuilder sb, int spaces) {
        return sb.append(" ".repeat(Math.max(0, spaces)));
    }

    private static int countLeadingSpaces(String line) {
        var count = 0;
        while (count < line.length() && line.charAt(count) == ' ') count++;
        return count;
    }

    /** Print the YAML formát */
    public String toString() {
        return toYaml();
    }

    // --- UTILITIES ---

    /** Convert YAML to PROPERTIES */
    public static String convertYamlToProps(String yaml) {
        return TreeModel.ofYaml(yaml).toProps();
    }

    /** Convert PROPERTIES to YAML */
    public static String convertPropsToYaml(String properties) {
        return TreeModel.ofProps(properties).toYaml();
    }
}
