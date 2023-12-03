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

public final class PPUtils {

    private final PrintStream out;

    public PPUtils(PrintStream out) {
        this.out = out;
    }

    public static void main(final String[] args) throws Exception {
        new PPUtils(System.out).start(List.of(args));
    }

    void start(final List<String> args) throws IOException {
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
                var result = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM'T'HHmm"));
                out.println(result);
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
            case "find" -> {
                final var file = Path.of(args.get(1));
                final var subArgs = args.subList(2, args.size());
                final var bodyPattern = build(subArgs, -2, t -> Pattern.compile(t));
                final var filePattern = build(subArgs, -1, t -> Pattern.compile(t));
                new FinderUtilitiy(bodyPattern, filePattern, out).printAllFiles(file);
            }
            default -> {
                out.println("Use one of the commands: date, time, datetime, grep, find");
                System.exit(-1);
            }
        }
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