/*
 * Common utilities for Java17+ for the CLI (command line interface).
 * Usage: java PPUtils.java
 *
 * Add the function to the Powershell config ("%USERPROFILE%"\Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1) on Windows:
 * function ppUtils { $javaClass = Join-Path -Path $env:UserProfile -ChildPath 'bin\PPUtils.java'; java $javaClass $args }
 * function findy { pputils find . $args }
 * function grepy { pputils grep . $args }
 *
 * Add the function to the Bash config ("$HOME"/.bash_aliases) on Linux Ubuntu:
 * ppUtils() { javaExe $HOME/bin/PPUtils.java "$@"; }
 * findy() { ppUtils find . "$@"; }
 * grepy() { ppUtils grep . "$@"; }
 *
 * Author: Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 * Licence: Apache License, Version 2.0
 */

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

    private final String appVersion = "1.0.3";

    private final PrintStream out;

    private final String dateIsoFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    protected static final String grepSeparator = ":";

    protected static final boolean sortDirectoryLast = true;

    public PPUtils(PrintStream out) {
        this.out = out;
    }

    public static void main(final String[] args) throws Exception {
        new PPUtils(System.out).start(Array.of(args));
    }

    void start(Array<String> args) throws IOException {
        final var enforcedLinux = args.getFirst().orElse("").equals("linux");
        if (enforcedLinux) {
            args = args.removeFirst();
        }
        var statement = args.getFirst().orElse("");
        switch (statement) {
            case "find" -> { // Example: find [--print] public.+interface java$
                final var file = args.get(1).map(t -> Path.of(t)).get();
                final var printLine = args.get(2).orElse("").equals("--print");
                final var subArgs = args.subArray(2 + (printLine ? 1 : 0 ));
                final var bodyPattern = subArgs.get(-2).map(t -> Pattern.compile(t)).orElse(null);
                final var filePattern = subArgs.get(-1).map(t -> Pattern.compile(t)).orElse(null);
                new FinderUtilitiy(bodyPattern, filePattern, enforcedLinux, out)
                        .findFiles(file, printLine && bodyPattern != null);
            }
            case "grep" -> {
                if (args.size() > 3) {
                    final var bodyPattern = args.get(2).map(t -> Pattern.compile(t)).orElse(null); // Pattern.CASE_INSENSITIVE);
                    final var pathFinder = new FinderUtilitiy(bodyPattern, null, enforcedLinux, out);
                    args.stream().skip(3).forEach(file -> {
                        pathFinder.grep(Path.of(file), true);
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
                out.println("%s v%s: Use an one of the next commands:\nfind" +
                        ", grep, date, time, datetime" +
                        ", date-iso, date-format" +
                        ", base64encode, base64decode"
                                .formatted(getClass().getSimpleName(), appVersion));
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
        /** @Nullable */
        private final Pattern bodyPattern;
        /** @Nullable */
        private final Pattern filePattern;
        private final boolean enforcedLinux;
        private final PrintStream out;

        public FinderUtilitiy(Pattern bodyPattern, Pattern filePattern, boolean enforcedLinux, PrintStream out) {
            this.bodyPattern = bodyPattern;
            this.filePattern = filePattern;
            this.enforcedLinux = enforcedLinux;
            this.out = out;
        }

        public void findFiles(Path dir, boolean printLine) throws IOException {
            Files.list(dir)
                    .filter(Files::isReadable)
                    .sorted(sortDirectoryLast ? new DirLastComparator() : Comparator.naturalOrder())
                    .forEach(file -> {
                if (Files.isDirectory(file)) {
                    try {
                        findFiles(file, printLine);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if ((filePattern == null || filePattern.matcher(file.toString()).find())
                        && (bodyPattern == null || grep(file, printLine))) {
                    printFileName(file).println();
                }
            });
        }

        public boolean grep(Path file, boolean printLine) {
            try {
                final var validLineStream = Files
                        .lines(file, StandardCharsets.UTF_8)
                        .filter(row -> bodyPattern == null || bodyPattern.matcher(row).find());
                if (printLine) {
                    validLineStream.forEach(line -> printFileName(file).printf("%s%s%n", grepSeparator, line));
                    return false;
                } else {
                    return validLineStream.findFirst().isPresent();
                }
            } catch (UncheckedIOException e) {
                return false;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /** Method supports a GitBash shell. */
        protected PrintStream printFileName(Path path) {
            if (enforcedLinux) {
                out.print(path.toString().replace('\\', '/'));
            } else {
                out.print(path);
            }
            return out;
        }
    }

    /** Compare files by a name, the directory last */
    static class DirLastComparator implements Comparator<Path> {
        @Override
        public int compare(final Path p1, final Path p2) {
            final var d1 = Files.isDirectory(p1);
            final var d2 = Files.isDirectory(p2);
            if (d1 != d2) {
                return d1 ? 1 : -1;
            } else {
                return p1.getFileName().toString().compareTo(p1.getFileName().toString());
            }
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
            final var j = i >= 0 ? i : array.length + i;
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
            final var from2 = Math.min(from, array.length);
            final var result = Arrays.copyOfRange(array, from2, array.length);
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

        @Override
        public String toString() {
            return List.of(array).toString();
        }

        @SuppressWarnings("unchecked")
        public static <T> Array<T> of(T... chars) {
            return new Array<T>(chars);
        }
    }
}