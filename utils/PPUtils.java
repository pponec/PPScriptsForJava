// Common utilities for Java17+ for the CLI (command line interface).
// Usage $ java utils.PPUtils.java
// Licence: Apache License, Version 2.0, https://github.com/pponec/

package utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Usage and examples:
 * <ul>
 *    <li>{@code java PPUtils find main.*String java$ } - find readable files by regular expressions.</li>
 *    <li>{@code java PPUtils grep main.*String PPUtils.java } - find readable file rows by a regular expression.</li>
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
        new PPUtils(System.out).start(Array.of(args));
    }

    void start(Array<String> args) throws IOException {
        final var enforcedLinux = args.getFirst().orElse("").equals("linux");
        if (enforcedLinux) {
            args.removeFirst();
        }
        var statement = args.getFirst().orElse("");
        switch (statement) {
            case "find" -> {
                final var file = args.get(1).map(t -> Path.of(t)).get();
                final var subArgs = args.subArray(2);
                final var bodyPattern = subArgs.get(-2).map(t -> Pattern.compile(t)).get();
                final var filePattern = subArgs.get(-1).map(t -> Pattern.compile(t)).get();
                new FinderUtilitiy(bodyPattern, filePattern, enforcedLinux, out).printAllFiles(file);
            }
            case "grep" -> {
                if (args.size() > 2) {
                    final var bodyPattern = args.get(1).map(t -> Pattern.compile(t)).get(); // Pattern.CASE_INSENSITIVE);
                    final var pathFinder = new FinderUtilitiy(bodyPattern, null, enforcedLinux, out);
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
                out.println(currentDate(args.get(1).orElseThrow(() -> new IllegalArgumentException(
                        "Use some format, for example: \"%s\"".formatted(dateIsoFormat)))));
            }
            case "base64encode" -> {
                new Converters(out).convertBase64(args.get(1).orElse(""), true);
            }
            case "base64decode" -> {
                new Converters(out).convertBase64(args.get(1).orElse(""), false);
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

    static final class Converters {

        private final PrintStream out;

        public Converters(PrintStream out) {
            this.out = out;
        }

        /** Encode a decode file by the Base64 */
        public void convertBase64(String inpFile, boolean encode) throws IOException {
            if (inpFile.isEmpty()) {
                throw new IllegalArgumentException("No file was not found");
            }
            convertBase64(Path.of(inpFile), encode);
        }

        /** Encode a decode file by the Base64 */
        public void convertBase64(Path inpFile, boolean encode) throws IOException {
            final var inpFileName = inpFile.getFileName().toString();
            final var outFile = encode
                    ? inpFile.resolveSibling(inpFileName + ".base64")
                    : inpFile.resolveSibling(inpFileName.substring(0, inpFileName.lastIndexOf(".")));
            final var encoder = Base64.getEncoder();
            final var decoder = Base64.getDecoder();
            final var buffer = new byte[2000];
            var byteCount = 0;
            try (
                    final var is = encode ? Files.newInputStream(inpFile) : decoder.wrap(Files.newInputStream(inpFile));
                    final var os = encode ? encoder.wrap(Files.newOutputStream(outFile)) : Files.newOutputStream(outFile);
            ) {
                while ((byteCount = is.read(buffer)) != -1) {
                    final var b2 = byteCount < buffer.length
                            ? Arrays.copyOfRange(buffer, 0, byteCount)
                            : buffer;
                    os.write(b2);
                }
            }
            out.println("Converted file has a name: '%s'".formatted(outFile));
        }
    }

    static final class FinderUtilitiy {
        private final Pattern bodyPattern;
        private final Pattern filePattern;
        private final boolean enforcedLinux;
        private final PrintStream out;

        public FinderUtilitiy(Pattern bodyPattern, Pattern filePattern, boolean enforcedLinux, PrintStream out) {
            this.bodyPattern = bodyPattern;
            this.filePattern = filePattern;
            this.enforcedLinux = enforcedLinux;
            this.out = out;
        }

        public void printAllFiles(Path dir) throws IOException {
            Files.list(dir)
                    .filter(Files::isReadable)
                    .sorted(Comparator.naturalOrder())
                    .forEach(file -> {
                if (Files.isDirectory(file)) {
                    try {
                        printAllFiles(file);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if ((filePattern == null || filePattern.matcher(file.toString()).find())
                        && (bodyPattern == null || grep(file, true))) {
                    print(file).println();
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
                    validRows.forEach(row -> print(file).printf(": %s%n", file, row));
                    return true;
                }
            } catch (UncheckedIOException e) {
                return false;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /** Method supports a GitBash shell. */
        protected PrintStream print(Path path) {
            if (enforcedLinux) {
                out.print(path.toString().replace('\\', '/'));
            } else {
                out.print(path);
            }
            return out;
        }
    }

    /** The immutable Array wrapper with utilities (from the Ujorm framework) */
    static class Array<T> {
        protected final T[] array;

        public Array(T[] array) {
            this.array = array;
        }

        /** Negative index is supported */
        public Optional<T> get(final int i) {
            final var j = i >= 0 ? i : array.length - i;
            return Optional.ofNullable(j >= 0 && j < array.length ? array[j] : null);
        }

        /** Add new items to the new Array */
        @SuppressWarnings("unchecked")
        public Array<T> add(final T... toAdd) {
            final var result = Arrays.copyOf(array, array.length + toAdd.length);
            System.arraycopy(toAdd, 0, result, array.length, toAdd.length);
            return new Array<>(result);
        }

        /** Negative index is supported */
        public T getItem(final int i) {
            return array[i >= 0 ? i : array.length + i];
        }

        public Optional<T> getFirst() {
            return get(0);
        }

        public Optional<T> getLast() {
            return get(-1);
        }

        public Array<T> removeFirst() {
            final var result = array.length > 0 ? Arrays.copyOfRange(array, 1, array.length) : array;
            return new Array<>(result);
        }

        public Array<T> subArray(final int from) {
            final var result = Arrays.copyOfRange(array, from, array.length);
            return new Array<>(result);
        }

        public List<T> toList() {
            return List.of(array);
        }

        public Stream<T> stream() {
            return Stream.of(array);
        }

        @SuppressWarnings("unchecked")
        public T[] toArray() {
            final var type = array.getClass().getComponentType();
            final var result =  java.lang.reflect.Array.newInstance(type, array.length);
            System.arraycopy(array, 0, result, 0, array.length);
            return (T[]) result;
        }

        public boolean isEmpty() {
            return array.length == 0;
        }

        public boolean hasLength() {
            return array.length > 0;
        }

        public int size() {
            return array.length;
        }

        @SuppressWarnings("unchecked")
        public static <T> Array<T> of(T... chars) {
            return new Array<T>(chars);
        }
    }
}