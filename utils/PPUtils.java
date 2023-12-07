// Common utilities for Java17+ for the CLI (command line interface).
// Usage $ java utils.CommonUtilities.java
// Licence: Apache License, Version 2.0, https://github.com/pponec/

package utils;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Usage and examples:
 * <ul>
 *    <li>{@code java PPUtils find main.*String java$ } - find files by regular expressions.</li>
 *    <li>{@code java PPUtils grep main.*String PPUtils.java } - find file rows by a regular expression.</li>
 *    <li>{@code java PPUtils date} - prints a date by ISO format, for example: "2023-12-31"</li>
 *    <li>{@code java PPUtils time} - prints hours and time, for example "2359"</li>
 *    <li>{@code java PPUtils datetime} - prints datetime format "2023-12-31T2359"</li>
 *    <li>{@code java PPUtils date-iso} - prints datetime by ISO format, eg: "2023-12-31T23:59:59.999"</li>
 *    <li>{@code java PPUtils date-format "yyyy-MM-dd'T'HH:mm:ss.SSS"} - prints a time by a custom format</li>
 * </ul>
 */
public final class PPUtils {

    private final PrintStream out;

    private final String dateIsoFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    public PPUtils(PrintStream out) {
        this.out = out;
    }

    public static void main(final String[] args) throws Exception {
        new PPUtils(System.out).start(List.of(args));
    }

    void start(final List<String> args) throws IOException {
        var statement = args.isEmpty() ? "" : args.get(0);
        switch (statement) {
            case "find" -> {
                final var file = Path.of(args.get(1));
                final var subArgs = args.subList(2, args.size());
                final var bodyPattern = build(subArgs, -2, t -> Pattern.compile(t));
                final var filePattern = build(subArgs, -1, t -> Pattern.compile(t));
                new FinderUtilitiy(bodyPattern, filePattern, out).printAllFiles(file);
            }
            case "grep" -> {
                if (args.size() > 2) {
                    final var bodyPattern = build(args, 1, t -> Pattern.compile(t)); // Pattern.CASE_INSENSITIVE);
                    final var pathFinder = new FinderUtilitiy(bodyPattern, null, out);
                    args.stream().skip(2).forEach(file -> {
                        pathFinder.grep(Path.of(file), false);
                    });
                }
            }
            case "date" -> {
                out.println(currentDate("yyyy-MM-dd"));
            }
            case "time" -> {
                out.println(currentDate("HHmm"));
            }
            case "datetime" -> {
                out.println(currentDate("yyyy-MM'T'HHmm"));
            }
            case "date-iso" -> {
                out.println(currentDate(dateIsoFormat));
            }
            case "date-format" -> {
                if (args.size() <= 1) {
                    throw new IllegalArgumentException("Use some format, for example: \"%s\""
                            .formatted(dateIsoFormat));
                }
                out.println(currentDate(args.get(1)));
            }
            default -> {
                out.println("Use an one of the next commands: find, grep, date, time, datetime, date-iso, date-format");
                System.exit(-1);
            }
        }
    }

    private String currentDate(String format) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    }

    private <T> T build(List<String> args, int index, Function<String, T> func) {
        final var idx = index < 0 ? args.size() + index : index;
        final var text = (idx >= 0 && idx < args.size()) ? args.get(idx) : null;
        return text != null ? func.apply(text) : null;
    }

    static final class FinderUtilitiy {
        private final Pattern bodyPattern;
        private final Pattern filePattern;
        private final PrintStream out;

        public FinderUtilitiy(Pattern bodyPattern, Pattern filePattern, PrintStream out) {
            this.bodyPattern = bodyPattern;
            this.filePattern = filePattern;
            this.out = out;
        }

        public void printAllFiles(Path dir) throws IOException {
            Files.list(dir).sorted(Comparator.naturalOrder()).forEach(file -> {
                if (Files.isDirectory(file)) {
                    try {
                        printAllFiles(file);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if ((filePattern == null || filePattern.matcher(file.toString()).find())
                        && (bodyPattern == null || grep(file, true))) {
                    out.println(file);
                }
            });
        }

        public boolean grep(Path file, boolean isPresent) {
            try {
                final Stream<String> validRows = Files
                        .lines(file, StandardCharsets.UTF_8)
                        .filter(row -> bodyPattern == null || bodyPattern.matcher(row).find());
                if (isPresent) {
                    return validRows.findFirst().isPresent();
                } else {
                    validRows.forEach(row -> out.printf("%s: %s%n", file, row));
                    return true;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}