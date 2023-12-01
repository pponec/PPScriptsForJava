// Common utilities for Java17+
// Usage $ java CommonUtilities.java
// Licence: Apache License, Version 2.0, https://github.com/pponec/

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

public final class CommonUtilities {

    private final PrintStream out;

    public CommonUtilities(PrintStream out) {
        this.out = out;
    }

    public static void main(final String[] args) throws Exception {
        new CommonUtilities(System.out).start(List.of(args));
    }

    void start(final List<String> args) throws Exception {
        var statement = args.isEmpty() ? "" : args.get(0);
        switch (statement) {
            case "date" -> {
                var result = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                out.println(result);
            }
            case "time" -> {
                var result = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
                out.println(result);
            }
            case "datetime" -> {
                var result = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"));
                out.println(result);
            }
            case "grep" -> {
                if (args.size() > 2) {
                    final var pattern = Pattern.compile(args.get(1)); // Pattern.CASE_INSENSITIVE);
                    for (int i = 2, max = args.size(); i < max; ++i) {
                        final var file = args.get(i);
                        try (var rows = Files.lines(Path.of(file), StandardCharsets.UTF_8)) {
                            rows.forEach(row -> {
                                if (pattern.matcher(row).find()) {
                                    out.printf("%s: %s%n", file, row);
                                }
                            });
                        }
                    }
                }
            }
            default -> {
                out.println("Use one of the commands: date, time, datetime, grep");
                System.exit(-1);
            }
        }
    }
}