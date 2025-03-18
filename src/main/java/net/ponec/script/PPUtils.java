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

package net.ponec.script;

import javax.tools.ToolProvider;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Usage and examples:
 * <ul>
 *    <li>{@code java PPUtils.java find . 'main.*String' java$ } - find readable files by regular expressions. Partial compliance is assessed.</li>
 *    <li>{@code java PPUtils.java grep 'main.*String' PPUtils.java } - find readable file rows by a regular expression.</li>
 *    <li>{@code java PPUtils.java grepf 'class\s(\w+)' 'class:%s of ${file}' PPUtils.java} - grep file by grouped regexp and print result by the template.</li>
 *    <li>{@code java PPUtils.java grepf 'class\s(\w+)' 'class:%s of ${file}' --file file.txt} - grep file by grouped regexp and print result by the template.</li>
 *    <li>{@code java PPUtils.java date} - prints a date by ISO format, for example: "2023-12-31"</li>
 *    <li>{@code java PPUtils.java time} - prints hours and time, for example "2359"</li>
 *    <li>{@code java PPUtils.java datetime} - prints datetime format "2023-12-31T2359"</li>
 *    <li>{@code java PPUtils.java date-iso} - prints datetime by ISO format, eg: "2023-12-31T23:59:59.999"</li>
 *    <li>{@code java PPUtils.java date-format "yyyy-MM-dd'T'HH:mm:ss.SSS"} - prints a time by a custom format</li>
 *    <li>{@code java PPUtils.java base64encode "file.bin"} - encode any (binary) file.</li>
 *    <li>{@code java PPUtils.java base64decode "file.base64"} - decode base64 encoded file (result removes extension)</li>
 *    <li>{@code java PPUtils.java key json } - Get a value by the (composite) key, for example: {@code "a.b.c"}</li>
 *    <li>{@code java PPUtils.java archive  Archive.java File1 File2 Dir1 Dir2 } - Creates a self-extracting archive in Java class source code format. Recursive directories are supported.</li>
 *    <li>{@code java PPUtils.java archive  Archive.java --file FileList.txt } - Creates a self-extracting archive for all files from the file list.</li>
 *    <li>{@code java PPUtils.java archive1 Archive.java File1 File2 File3 } - Compress the archive to the one row. . Recursive directories are supported.</li>
 * </ul>
 * For more information see the <a href="https://github.com/pponec/PPScriptsForJava/blob/main/docs/PPUtils.md">GitHub page</a>.
 */
public final class PPUtils {

    private final String appName = getClass().getSimpleName();

    private final String appVersion = "1.2.7";

    private final Class<?> mainClass = getClass();

    private final PrintStream out;

    private final String dateIsoFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    private static final String grepSeparator = ":: ";

    private static final String fileSourceArg = "--file";

    private static final boolean sortDirectoryLast = true;

    private final String sourceUrl = "https://raw.githubusercontent.com/pponec/PPScriptsForJava/development/src/%s/java/net/ponec/script/%s.java"
            .formatted(true ? "main" : "development", appName);

    public PPUtils(PrintStream out) {
        this.out = out;
    }

    public static void main(final String[] args) throws Exception {
        new PPUtils(System.out).mainRun(Array.of(args));
    }

    void mainRun(Array<String> args) throws Exception {
        final var enforcedLinux = args.getFirst().orElse("").equals("linux");
        if (enforcedLinux) {
            args = args.removeFirst();
        }
        var statement = args.getFirst().orElse("");
        switch (statement) {
            case "find" -> { // Example: find [--printfileonly] public.+interface java$
                final var file = args.get(1).map(Path::of).get();
                final var fileOnly = args.get(2).orElse("").equals("--printfileonly");
                final var subArgs = args.subArray(2 + (fileOnly ? 1 : 0 ));
                final var bodyPattern = subArgs.get(-2).map(Pattern::compile).orElse(null);
                final var filePattern = subArgs.get(-1).map(Pattern::compile).orElseThrow(() ->
                        new IllegalArgumentException("No file pattern"));
                new Finder(pathComparator(), bodyPattern, "", filePattern, enforcedLinux, out)
                        .findFiles(file, !fileOnly && bodyPattern != null);
            }
            case "grep" -> {
                if (args.size() > 2) {
                    final var bodyPattern = args.get(1).map(Pattern::compile).orElse(null); // Pattern.CASE_INSENSITIVE);
                    final var finder = new Finder(pathComparator(), bodyPattern, "", null, enforcedLinux, out);
                    args.stream().skip(2).forEach(file -> finder.grep(Path.of(file), true));
                }
            }
            case "grepf" -> {
                if (args.size() > 3) {
                    final var bodyPattern = args.get(1).map(Pattern::compile).orElse(null); // Pattern.CASE_INSENSITIVE);
                    final var bodyFormat = args.get(2).orElse(""); // Pattern.CASE_INSENSITIVE);
                    final var finder = new Finder(pathComparator(), bodyPattern, bodyFormat, null, enforcedLinux, out);
                    final var files = fileSourceArg.equals(args.get(3).orElse("")) && args.size() > 4
                            ? readFiles(args.getItem(4))
                            : args.subArray(3);
                    files.stream().forEach(file -> finder.grep(Path.of(file), true));
                }
            }
            case "date" -> {
                out.println(currentDate("yyyy-MM-dd"));
            }
            case "time" -> {
                out.println(currentDate("HHmm"));
            }
            case "datetime" -> {
                out.println(currentDate("yyyy-MM-dd'T'HHmm"));
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
            case "json" -> {
                final var key = args.get(1).orElse("");
                final var json = Files.readString(Path.of(args.get(2).orElse("?")));
                out.println(Json.of(json).get(key).orElse(""));
            }
            case "sa", "saveArchive", "archive" -> {
                new ScriptArchiveBuilder(false).build(args.get(1).orElseThrow(), args.subArray(2));
            }
            case "sa1", "saveArchive1", "archive1" -> { // Compress the archive to the one row
                new ScriptArchiveBuilder(true).build(args.get(1).orElseThrow(), args.subArray(2));
            }
            case "compile" -> {
                new Utilities().compile();
            }
            case "version" -> {
                out.printf("%s v%s%n", appName, appVersion);
            }
            default -> {
                out.printf("%s v%s: Use an one of the next commands:\nfind" +
                        ", grep, grepf, date, time, datetime" +
                        ", date-iso, date-format" +
                        ", base64encode, base64decode, version" +
                        ", archive, archive1 %n"
                        , getClass().getSimpleName(), appVersion);
                System.exit(1);
            }
        }
    }

    static Comparator<Path> pathComparator() {
        return sortDirectoryLast
                ? new DirLastComparator()
                : Comparator.naturalOrder();
    }

    private String currentDate(String format) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    }

    private static Array<String> readFiles(String file) {
        try (Stream<String> lines = Files.lines(Path.of(file))) {
            final var result = Array.of(lines.toArray(String[]::new));
            result.stream()
                    .filter(f -> !Files.isRegularFile(Path.of(f)))
                    .findAny()
                    .ifPresent(f -> { throw new IllegalArgumentException("File '%s' was not found.".formatted(f)); });
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
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
            try ( final var is = encode ? Files.newInputStream(inpFile) : decoder.wrap(Files.newInputStream(inpFile));
                  final var os = encode ? encoder.wrap(Files.newOutputStream(outFile)) : Files.newOutputStream(outFile);
            ) { is.transferTo(os); }
            out.printf("Converted file has a name: '%s'%n", outFile);
        }
    }

    static final class Finder {
        private static String FILE_PATTEN = "${file}";
        /** @Nullable */
        private final Pattern bodyPattern;
        /** @NonNull */
        private final String bodyFormat;
        /** @NonNull */
        private final boolean printFileName;
        /** @Nullable */
        private final Pattern filePattern;
        private final boolean enforcedLinux;
        private final PrintStream out;
        private final Comparator<Path> pathComparator;

        public Finder(Comparator<Path> comparator, Pattern bodyPattern, String bodyFormat, Pattern filePattern, boolean enforcedLinux, PrintStream out) {
            this.pathComparator = comparator;
            this.bodyPattern = bodyPattern;
            this.bodyFormat = bodyFormat;
            this.printFileName = bodyFormat.contains(FILE_PATTEN);
            this.filePattern = filePattern;
            this.enforcedLinux = enforcedLinux;
            this.out = out;
        }

        public void findFiles(Path dir, boolean printLine) throws IOException {
            try (var fileStream = Files.list(dir)) {
                fileStream.filter(Files::isReadable)
                        .sorted(pathComparator)
                        .forEach(file -> {
                            if (Files.isDirectory(file)) {
                                try {
                                    findFiles(file, printLine);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            } else if ((filePattern == null || filePattern.matcher(file.toString()).find())
                                    && (bodyPattern == null || grep(file, printLine))) {
                                out.println(formatFileName(file));
                            }
                        });
            }
        }

        public boolean grep(Path file, boolean printLine) {
            try (final var validLineStream = Files
                    .lines(file, StandardCharsets.UTF_8)
                    .filter(line -> bodyPattern == null || bodyPattern.matcher(line).find())
            ) {
                if (printLine) {
                    validLineStream.forEach(line -> {
                        if (bodyFormat.isEmpty()) {
                            out.printf("%s%s%s%n", formatFileName(file), grepSeparator, line.trim());
                        } else {
                            var format = printFileName
                                    ? bodyFormat.replace(FILE_PATTEN, formatFileName(file))
                                    : bodyFormat;
                            out.println(formatGroupText(line.trim(), format));
                        }
                    });
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

        private String formatGroupText(String line, String bodyFormat) {
            final var matcher = bodyPattern.matcher(line);
            if (matcher.find()) {
                var groups = new Object[matcher.groupCount()];
                for (int i = 0; i < groups.length; i++) {
                    groups[i] = matcher.group(i + 1);
                }
                return bodyFormat.formatted(groups);
            } else {
                return "";
            }
        }

        /** Method supports a GitBash shell. */
        private String formatFileName(Path path) {
            return enforcedLinux
                    ? path.toString().replace('\\', '/')
                    : path.toString();
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
                return p1.getFileName().toString().compareTo(p2.getFileName().toString());
            }
        }
    }
    /** Build a script archiv */
    public static final class ScriptArchiveBuilder {
        final boolean oneRowClass;
        ScriptArchiveBuilder(boolean oneRowClass) { this.oneRowClass = oneRowClass; }
        private final String homeUrl = "https://github.com/pponec/PPScriptsForJava/blob/main/docs/PPUtils.md";
        public void build(String archiveFile, Array<String> files) throws IOException {
            if (fileSourceArg.equals(files.getFirst().orElse("")) && files.size() == 2) {
                files = readFiles(files.getItem(1));
            }
            build(Path.of(archiveFile), findInnerFiles(files));
            System.out.printf("%s.%s: archive created: %s%n", PPUtils.class.getSimpleName(), getClass().getSimpleName(), archiveFile);
        }

        public Set<Path> findInnerFiles(Array<String> items) {
            final var result = new TreeSet<Path>();
            items.stream()
                    .distinct()
                    .map(item -> Paths.get(item))
                    .filter(Files::isReadable)
                    .forEach(path -> {
                        if (Files.isRegularFile(path)) {
                            result.add(path);
                        } else if (Files.isDirectory(path) && Files.isReadable(path)) {
                            try (var files = Files.walk(path)) {
                                result.addAll(files.filter(Files::isRegularFile).collect(Collectors.toList()));
                            } catch (IOException e) {
                                throw new RuntimeException(path.toString(), e);
                            }
                        }
                    });
            return result;
        }
        public void build(Path javaArchiveFile, Collection<Path> files) throws IOException {
            validate(javaArchiveFile, files);
            var splitSequence = "@@@";
            var cFile = javaArchiveFile.getFileName().toString();
            var dotIndex = cFile.indexOf('.');
            if (dotIndex > 0) { cFile = cFile.substring(0, dotIndex); }
            var classBody = """
                    /* Extract files by: java %s.java
                     * Powered by the <a href="%s">PPUtils</a>.
                     * @version %s */
                    import java.io.*;
                    import java.nio.file.*;
                    import java.util.*;
                    import java.util.zip.*;
                    import java.util.stream.Stream;
                    public final class %s {
                        public static void main(String[] args) {
                            Stream.of(null %s
                            ).skip(1).forEach(file -> write(file));
                        }
                        static void write(File file) {
                            try {
                                var path = Path.of(file.path);
                                if (path.getParent() != null) Files.createDirectories(path.getParent());
                                var base64is = new Base64InputStream(file.base64Body);
                                var is = new InflaterInputStream(Base64.getDecoder().wrap(base64is), new Inflater());
                                try (var os = new PrintStream(Files.newOutputStream(path))) { is.transferTo(os); }
                                System.out.println("Restored: " + path);
                            } catch (IOException e) {
                                throw new IllegalArgumentException("Failed to extract file: " + file.path, e);
                            }
                        }
                        record File(String path, String... base64Body) {}
                        static final class Base64InputStream extends InputStream {
                            private final StringReader[] readers;
                            private final byte[] oneByte = new byte[1];
                            private char[] buffer = new char[0];
                            private int idx;
                            public Base64InputStream(String... body) {
                                this.readers = Arrays.stream(body).map(r -> new StringReader(r)).toArray(StringReader[]::new);
                            }
                            @Override public int read(final byte[] b, final int off, final int len) throws IOException {
                                if (buffer.length < len) { buffer = new char[len]; }
                                final var result = readers[idx].read(buffer, off, len);
                                for (int i = 0; i < result; i++) { b[i] = (byte) buffer[i]; }
                                return (result >= 0 || ++idx == readers.length) ? result : read(b, off, len);
                            }
                            @Override public int read() throws IOException {
                                final var result = read(oneByte, 0, 1);
                                return result < 0 ? result : oneByte[0];
                            }
                            @Override public void close() { Stream.of(readers).forEach(r -> r.close()); }
                            @Override public long skip(long n) throws IOException { throw new UnsupportedEncodingException(); }
                        }
                    }
                    """.formatted(cFile, homeUrl, LocalDateTime.now(), cFile, splitSequence, "%s")
                    .split(splitSequence);
            try (var os = new PrintStream(new BufferedOutputStream(Files.newOutputStream(javaArchiveFile)), false, StandardCharsets.UTF_8)) {
                print(classBody[0], os);
                for (var file : files) {
                    print("\n\t\t, new File(\"", os);
                    print(file.toString().replace('\\', '/'), os);
                    print("\", \"", os);
                    try (var fis = Files.newInputStream(file)) {
                        final var eos = new SplitOutputStream(os);
                        final var b64os = Base64.getEncoder().wrap(eos);
                        try (var dos = new DeflaterOutputStream(b64os, new Deflater())) {
                            fis.transferTo(dos);
                        }
                    }
                    print("\")", os);
                }
                print(classBody[1], os);
            }
        }
        public void print(String body, PrintStream out) {
            if (oneRowClass) {
                out.print(body.trim().replaceAll("\\s+", " ") // remove double spaces
                        .replaceAll("/\\*.*\\*/ *", "")       // remove comment(s)
                        .replaceAll(" (=+) ", "$1")
                        .replaceAll("([{};]) ", "$1")
                        .replace("private ", "")
                        .replace("final ", ""));
            } else {
                out.print(body);
            }
        }
        public void validate(Path classFile, Collection<Path> files) {
            if (!classFile.getFileName().toString().endsWith(".java")) {
                throw new IllegalArgumentException("The archive must be a Java file: " + classFile);
            }
            if (files.isEmpty()) {
                throw new IllegalArgumentException("No file to archive as found");
            }
            files.forEach(file -> {
                if (!Files.isRegularFile(file) || !Files.isReadable(file))  {
                    throw new IllegalArgumentException("The file was not found" + file);
                }
            });
        }

        static final class SplitOutputStream extends FilterOutputStream {
            final byte[] separator = "\",\"".getBytes(StandardCharsets.UTF_8);
            /* https://stackoverflow.com/questions/77417411/why-is-the-maximum-string-literal-length-in-java-65534 */
            final int group = 65534;
            private int counter = 0;

            public SplitOutputStream(OutputStream out) {
                super(out);
            }
            @Override public void write(final int b) throws IOException {
                if (++counter % group == 0) {
                    out.write(separator);
                }
                out.write(b);
            }
            @Override
            public void write(final byte[] b, final int off, final int len) throws IOException {
                for (int i = off, max = off + len; i < max; i++) {
                    write(b[i]);
                }
            }
            @Override
            public void close() throws IOException { /* Do nothing */ }
        }
    }

    //  ~ ~ ~ ~ ~ ~ ~ UTILITIES ~ ~ ~ ~ ~ ~ ~

    class Utilities {

        /** Compile the script and build it to the executable JAR file */
        private void compile() throws Exception {
            if (isJar()) {
                out.printf("Use the statement rather: java %s.java c", appName);
                System.exit(1);
            }

            var scriptDir = getScriptDir();
            var jarExe = "%s/bin/jar".formatted(System.getProperty("java.home"));
            var jarFile = "%s.jar".formatted(appName);
            var fullJavaClass = "%s/%s.java".formatted(scriptDir, appName);
            removePackage(Path.of(fullJavaClass));

            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("No Java Compiler is available");
            }
            var error = new ByteArrayOutputStream();
            var result = compiler.run(null, null, new PrintStream(error), fullJavaClass);
            if (result != 0) {
                throw new IllegalStateException(error.toString());
            }

            var classFiles = getAllClassFiles(mainClass);
            // Build a JAR file:
            var arguments = Array.of(jarExe, "cfe", jarFile, appName).add(classFiles);
            var process = new ProcessBuilder(arguments.toArray())
                    .directory(new File(classFiles[0]).getParentFile())
                    .start();
            var err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            var exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(err);
            }

            // Delete all classes:
            deleteClasses(classFiles);
        }

        private String getScriptDir() {
            var exePath = getPathOfRunningApplication();
            return exePath.substring(0, exePath.lastIndexOf(appName) - 1);
        }

        private boolean isJar() {
            return getPathOfRunningApplication().toLowerCase(Locale.ENGLISH).endsWith(".jar");
        }

        /**
         * Get a full path to this source Java file.
         */
        private String getSrcPath() {
            return "%s/%s.java".formatted(getScriptDir(), appName);
        }

        private String getPathOfRunningApplication() {
            final var enforcedLinux = false;
            final var protocol = "file:/";
            try {
                final var location = mainClass.getProtectionDomain().getCodeSource().getLocation();
                var result = location.toString();
                if (enforcedLinux && isSystemMsWindows() && result.startsWith(protocol)) {
                    result = result.substring(protocol.length());
                } else {
                    result = location.getPath();
                }
                return URLDecoder.decode(result, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "%s.%s".formatted(appName, "java");
            }
        }

        private void deleteClasses(String... classFiles) {
            Stream.of(classFiles).forEach(f -> {
                try {
                    Files.delete(Path.of(f));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        private boolean isSystemMsWindows() {
            return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
        }

        private String[] getAllClassFiles(Class<?> mainClass) {
            final var result = new ArrayList<String>();
            final var suffix = ".class";
            result.add(mainClass.getSimpleName() + suffix);
            Stream.of(mainClass.getDeclaredClasses())
                    .map(c -> mainClass.getSimpleName() + '$' + c.getSimpleName() + suffix)
                    .forEach(result::add);
            return result.toArray(String[]::new);
        }

        private void download() throws IOException, InterruptedException {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Files.writeString(Path.of(getSrcPath()), response.body());
            } else {
                throw new IllegalStateException("Downloading error code: %s".formatted(response.statusCode()));
            }
        }

        private void removePackage(Path fullJavaClass) throws IOException {
            var packageRegexp = "package %s;".formatted(mainClass.getPackageName());
            out.println("packageRegexp: "  + packageRegexp);
            var script = Files.readString(fullJavaClass);
            script = script.replaceFirst(packageRegexp, "");
            Files.writeString(fullJavaClass, script);
        }
    }


    /** The immutable Array wrapper with utilities (from the Ujorm framework) */
    record Array<T>(T[] array) {

        public Array<T> clone() {
            return new Array<>(toArray());
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

        /** @param from Negative value is supported */
        public Array<T> subArray(final int from) {
            final var frm = from < 0 ? array.length + from : from;
            final var result = Arrays.copyOfRange(array, Math.min(frm, array.length), array.length);
            return new Array<>(result);
        }

        public List<T> toList() {
            return List.of(array);
        }

        public Set<T> toSet() {
            return Set.of(array);
        }

        public Stream<T> stream() {
            return Stream.of(array);
        }

        @SuppressWarnings("unchecked")
        public T[] toArray() {
            final var type = array.getClass().getComponentType();
            final var result = (T[]) java.lang.reflect.Array.newInstance(type, array.length);
            System.arraycopy(array, 0, result, 0, array.length);
            return result;
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
        public int hashCode() {
            return Arrays.hashCode(array);
        }

        @Override
        public boolean equals(final Object obj) {
            return (obj instanceof Array objArray) && Arrays.equals(array, objArray.array);
        }

        @Override
        public String toString() {
            return List.of(array).toString();
        }

        @SuppressWarnings("unchecked")
        public static <T> Array<T> of(T... chars) {
            return new Array<>(chars);
        }
    }

    /** JSON parser. The {@code array} type is not supported. <br>
     *  Java-style comments are tolerated as long as they do not contain quotes. */
    public static class Json {
        static final Pattern keyPattern = Pattern.compile("\"(.*?)\"\\s*:\\s*(\".*?\"|\\d+\\.?\\d*|true|false|null|\\{.*?\\})");
        final Map<String, Object> map;

        private Json(Map<String, Object> map) {
            this.map = map;
        }

        /** JSON Parser */
        public static Json of(String jsonString) {
            final var result = new HashMap<String, Object>();
            final var matcher = keyPattern.matcher(jsonString);
            while (matcher.find()) {
                result.put(matcher.group(1), parseValue(matcher.group(2)));
            }
            return new Json(result);
        }

        private static Object parseValue(final String textValue) {
            return switch (textValue) {
                case "true" -> true;
                case "false" -> false;
                case "null" -> null;
                default -> textValue.charAt(0) == '"' && textValue.charAt(textValue.length() - 1) == '"'
                        ? textValue.substring(1, textValue.length() - 1)
                        : textValue.indexOf('.') >= 0 ? Double.parseDouble(textValue)
                        : textValue.startsWith("{") ? of(textValue)
                        : Long.parseLong(textValue);
            };
        }

        /** Get a value by the (composite) key. For example: {@code json.get("a.b.c").get()} */
        public Optional<Object> get(String keys) {
            var result = (Object) null;
            var json = this;
            for (var key : keys.split("\\.")) {
                result = json.map.get(key);
                json = (result instanceof Json j) ? j : new Json(Map.of());
            }
            return Optional.ofNullable(result);
        }

        @Override
        public String toString() {
            return map.toString();
        }
    }
}