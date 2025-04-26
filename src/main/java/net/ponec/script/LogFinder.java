package net.ponec.script;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * The {@code LogFinder} class searches for lines matching a given regular expression
 * within files or directories provided as input.
 * <p>
 * If no path is specified, the current working directory is used by default.
 * The program processes:
 * <ul>
 *   <li>Text files (extensions: txt, log, csv, md)</li>
 *   <li>Text files contained within ZIP archives</li>
 * </ul>
 * When a matching line is found, it prints a configurable number of preceding
 * and following lines for better context.
 * <p>
 * The class handles plain text files as well as ZIP files without recursion into subdirectories.
 */
public class LogFinder {

    private static final int PREVIOUS_LINES = 3;
    private static final int FOLLOWING_LINES = 5;

    public static void main(String[] args) throws IOException {
        new LogFinder()._main(List.of(args));
    }

    public void _main(List<String> args) throws IOException {
        if (args.isEmpty()) {
            System.out.printf("Usage: java %s.java [regexpr] [dir_or_files]", getClass().getSimpleName());
            System.exit(1);
        }

        var regex = Pattern.compile(args.getFirst("ERROR"));
        var paths = args.size() > 1
                ? args.subList(1).stream().map(Paths::get).toList()
                : List.of(Paths.get("."));

        for (var path : paths) {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    for (var file : stream.toList()) {
                        if (Files.isRegularFile(file)) {
                            processFile(file, regex);
                        }
                    }
                }
            } else if (Files.isRegularFile(path)) {
                processFile(path, regex);
            }
        }
    }

    void processFile(Path file, Pattern pattern) throws IOException {
        var filename = file.getFileName().toString().toLowerCase();

        if (filename.endsWith(".zip")) {
            try (var zip = new ZipInputStream(Files.newInputStream(file))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.isDirectory() && entry.getName().matches(".*\\.(txt|log|csv|md)")) {
                        processTextStream(zip, entry.getName(), pattern);
                    }
                }
            }
        } else {
            if (filename.matches(".*\\.(txt|log|csv|md)")) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    processTextReader(reader, file.toString(), pattern);
                }
            }
        }
    }

    void processTextStream(InputStream stream, String name, Pattern pattern) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            processTextReader(reader, name, pattern);
        }
    }

    void processTextReader(BufferedReader reader, String sourceName, Pattern pattern) throws IOException {
        var buffer = new CircularBuffer(PREVIOUS_LINES);
        var lines = new ArrayList<String>();
        var line = "";

        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }

        for (var i = 0; i < lines.size(); i++) {
            var current = lines.get(i);
            buffer.add(current);

            var matcher = pattern.matcher(current);
            if (matcher.find()) {
                System.out.println("--- " + sourceName + " ---");
                for (var s : buffer.getContents()) {
                    System.out.println(s);
                }
                for (var j = i + 1; j <= i + FOLLOWING_LINES && j < lines.size(); j++) {
                    System.out.println(lines.get(j));
                }
                System.out.println();
                buffer.clear();
            }
        }
    }

    /** Inner class CircularBuffer */
    static final class CircularBuffer {
        private final String[] data;
        private int index = 0;
        private int size = 0;

        public CircularBuffer(int capacity) {
            data = new String[capacity];
        }

        public void add(String line) {
            data[index] = line;
            index = (index + 1) % data.length;
            if (size < data.length) {
                size++;
            }
        }

        public List<String> getContents() {
            var result = List.<String>of();
            for (var i = 0; i < size; i++) {
                var pos = (index + i - size + data.length) % data.length;
                result.add(data[pos]);
            }
            return result;
        }

        public void clear() {
            Arrays.fill(data, null);
            index = 0;
            size = 0;
        }

        public String toString() {
            return String.join("\n", getContents());
        }
    }

    /** An extended ArrayList class */
    public static final class List<T> extends ArrayList<T> {

        private List(final Collection<T> c) {
            super(c);
        }

        public T get(final int i, final T defaultValue) {
            final var j = i >= 0 ? i : size() + i;
            final var result = j >= 0 && j < size()
                    ? get(j)
                    : defaultValue;
            return result != null ? result : defaultValue;
        }

        public Optional<T> getOptional(final int i) {
            return Optional.ofNullable(get(i, null));
        }

        public T getFirst(T defaultValue) {
            return get(0, defaultValue);
        }

        public T getLast(T defaultValue) {
            return get(-1, defaultValue);
        }

        @Override
        public List<T> clone() {
            super.clone();
            return new List<>(this);
        }

        public List<T> subList(int from) {
            final var size = size();
            final var from1 = from < 0 ? size + from : from;
            final var from2 = Math.min(from1, size);
            return new List<>(subList(from2, size));
        }

        public static <T> List<T> of(T... items) {
            return new List<T>(Arrays.asList(items));
        }

        public static <T> List<T> of(Collection<T> items) {
            return new List<T>(items);
        }

        public boolean isFilled() {
            return !isEmpty();
        }
    }
}
