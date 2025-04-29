package net.ponec.script;

import java.io.*;
import java.nio.charset.Charset;
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
 *
 * See the <a href="https://github.com/pponec/PPScriptsForJava/blob/development/src/main/java/net/ponec/script/LogFinder.java">source</a>.
 *
 * @version 2025-04-28
 */
public class LogFinder {

    private static final Pattern DEFAULT_REGEXP = Pattern.compile("(ERROR|SEVERE)");
    private static final int BEFORE_LINES = 3;
    private static final int AFTER_LINES = 100;

    private final Pattern textFiles = Pattern.compile("\\.(txt|log|csv|md)$");
    private final Charset charset = StandardCharsets.UTF_8;
    private final PrintStream out;
    private final int beforeLines;
    private final int afterLines;
    private String lastSource = "";

    LogFinder(final PrintStream out) {
        this(out, BEFORE_LINES, AFTER_LINES);
    }

    LogFinder(final PrintStream out, int beforeLines, int afterLines) {
        this.out = out;
        this.beforeLines = beforeLines;
        this.afterLines = afterLines;
    }

    public static void main(String[] args) throws IOException {
        new LogFinder(System.out).run(List.of(args));
    }

    public void run(List<String> args) throws IOException {
        if (args.isEmpty()) {
            out.printf("Usage: java %s.java [regexpr] [dir_or_files]", getClass().getSimpleName());
            System.exit(1);
        }

        var regtx = args.getFirst("");
        var regex = regtx.isEmpty() ? DEFAULT_REGEXP : Pattern.compile(args.get(0));
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
        if (filename.endsWith(".zip")) try (var zip = new ZipInputStream(Files.newInputStream(file))) {
            var entry = (ZipEntry) null;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && textFiles.matcher(entry.getName()).find()) {
                    var reader = new BufferedReader(new InputStreamReader(zip, charset));
                    processTextReader(reader, entry.getName(), pattern);
                }
            }
        } else if (textFiles.matcher(filename).find()) {
            try (var reader = Files.newBufferedReader(file, charset)) {
                processTextReader(reader, file.toString(), pattern);
            }
        }
    }

    void processTextReader(BufferedReader reader, String sourceName, Pattern pattern) throws IOException {
        var buffer = new CircularBuffer(beforeLines);
        var line = "";
        var afterCounter = 0;
        var lineCounter = 0;
        var eventCounter = 0;

        while ((line = reader.readLine()) != null) {
            lineCounter++;
            if (pattern.matcher(line).find()) {
                var firstLine = lineCounter - buffer.size();
                if (!lastSource.equals(sourceName)) {
                    lastSource = sourceName;
                    if (eventCounter++ > 0) out.println();
                    out.printf("### %s:%s #%s%n", sourceName, firstLine, eventCounter);
                }
                out.print(buffer.toStringLine());
                out.printf("[%s:%s] %s%n", sourceName, lineCounter, line);
                buffer.clear();
                afterCounter = this.afterLines;
            } else if (afterCounter-- > 0) {
                out.println(line);
            } else {
                buffer.add(line);
            }
        }
    }

    /** Inner class CircularBuffer */
    static final class CircularBuffer {
        private final String[] data;
        private int index = 0;
        private int size = 0;
        private String newLine = "\n";

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

        public int size() {
            return size;
        }

        public String toString() {
            return String.join(newLine, getContents());
        }

        public String toStringLine() {
            return size() > 0
                    ? this + newLine
                    : "";
        }
    }

    /** An extended ArrayList class */
    @SuppressWarnings({"unchecked", "serial"}) // Due obsolete Java 17
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
