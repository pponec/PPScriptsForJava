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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Usage and examples:
 * <ul>
 *    <li>{@code java PPUtils find main.*String java$ } - find readable files by regular expressions. Partial compliance is assessed.</li>
 *    <li>{@code java PPUtils grep main.*String PPUtils.java } - find readable file rows by a regular expression.</li>
 *    <li>{@code java PPUtils date} - prints a date by ISO format, for example: "2023-12-31"</li>
 *    <li>{@code java PPUtils time} - prints hours and time, for example "2359"</li>
 *    <li>{@code java PPUtils datetime} - prints datetime format "2023-12-31T2359"</li>
 *    <li>{@code java PPUtils date-iso} - prints datetime by ISO format, eg: "2023-12-31T23:59:59.999"</li>
 *    <li>{@code java PPUtils date-format "yyyy-MM-dd'T'HH:mm:ss.SSS"} - prints a time by a custom format</li>
 *    <li>{@code java PPUtils base64encode "file.bin"} - encode any (binary) file.</li>
 *    <li>{@code java PPUtils base64decode "file.base64"} - decode base64 encoded file (result removes extension)</li>
 *    <li>{@code java PPUtils key json } - Get a value by the (composite) key, for example: {@code "a.b.c"}</li>
 *    <li>{@code java PPUtils scriptArchive Archive.java File1 File2 File3 } - Creates a self-extracting archive in Java class source code format.</li>
 * </ul>
 */
public final class PPUtils {

    private final String appName = getClass().getSimpleName();

    private final String appVersion = "1.0.9";

    private final Class<?> mainClass = getClass();

    private final PrintStream out;

    private final String dateIsoFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    private static final String grepSeparator = ":";

    private static final boolean sortDirectoryLast = true;

    private final String sourceUrl = "https://raw.githubusercontent.com/pponec/PPScriptsForJava/development/src/%s/java/net/ponec/script/%s.java"
            .formatted(true ? "main" : "development", appName);

    public PPUtils(PrintStream out) {
        this.out = out;
    }

    public static void main(final String[] args) throws Exception {
        new PPUtils(System.out).start(Array.of(args));
    }

    void start(Array<String> args) throws Exception {
        final var enforcedLinux = args.getFirst().orElse("").equals("linux");
        if (enforcedLinux) {
            args = args.removeFirst();
        }
        var statement = args.getFirst().orElse("");
        switch (statement) {
            case "find" -> { // Example: find [--print] public.+interface java$
                final var file = args.get(1).map(Path::of).get();
                final var printLine = args.get(2).orElse("").equals("--print");
                final var subArgs = args.subArray(2 + (printLine ? 1 : 0 ));
                final var bodyPattern = subArgs.get(-2).map(Pattern::compile).orElse(null);
                final var filePattern = subArgs.get(-1).map(Pattern::compile).orElse(null);
                new FinderUtilitiy(pathComparator(), bodyPattern, filePattern, enforcedLinux, out)
                        .findFiles(file, printLine && bodyPattern != null);
            }
            case "grep" -> {
                if (args.size() > 3) {
                    final var bodyPattern = args.get(2).map(Pattern::compile).orElse(null); // Pattern.CASE_INSENSITIVE);
                    final var pathFinder = new FinderUtilitiy(pathComparator(), bodyPattern, null, enforcedLinux, out);
                    args.stream().skip(3).forEach(file -> pathFinder.grep(Path.of(file), true));
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
            case "sa", "scriptArchive" -> {
                new ScriptArchiveBuilder().build(args.get(1).orElse("ScriptArchive.java"), args.subArray(2).toList());
            }
            case "compile" -> {
                new Utilities().compile();
            }
            case "version" -> {
                out.printf("%s v%s%n", appName, appVersion);
            }
            default -> {
                out.printf("%s v%s: Use an one of the next commands:\nfind" +
                        ", grep, date, time, datetime" +
                        ", date-iso, date-format" +
                        ", base64encode, base64decode, version %n"
                        , getClass().getSimpleName(), appVersion);
                System.exit(1);
            }
        }
    }

    private Comparator<Path> pathComparator() {
        return sortDirectoryLast
                ? new DirLastComparator()
                : Comparator.<Path>naturalOrder();
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
            out.printf("Converted file has a name: '%s'%n", outFile);
        }
    }

    static final class FinderUtilitiy {
        /** @Nullable */
        private final Pattern bodyPattern;
        /** @Nullable */
        private final Pattern filePattern;
        private final boolean enforcedLinux;
        private final PrintStream out;
        private final Comparator<Path> pathComparator;

        public FinderUtilitiy(Comparator<Path> comparator, Pattern bodyPattern, Pattern filePattern, boolean enforcedLinux, PrintStream out) {
            this.pathComparator = comparator;
            this.bodyPattern = bodyPattern;
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
                                printFileName(file).println();
                            }
                        });
            }
        }

        public boolean grep(Path file, boolean printLine) {
            try (final var validLineStream = Files
                    .lines(file, StandardCharsets.UTF_8)
                    .filter(row -> bodyPattern == null || bodyPattern.matcher(row).find())
            ) {
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
        private PrintStream printFileName(Path path) {
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
                return p1.getFileName().toString().compareTo(p2.getFileName().toString());
            }
        }
    }

    /** Build a script archiv */
    static final class ScriptArchiveBuilder {
        public void build(String archiveFile, List<String> files) throws IOException {
            build(Path.of(archiveFile), files.stream().map(f -> Path.of(f)).toList());
        }
        public void build(Path classFile, List<Path> files) throws IOException {
            var splitSequence = "@@@";
            var cFile = classFile.getFileName().toString();
            var dotIndex = cFile.indexOf('.');
            if (dotIndex > 0) { cFile = cFile.substring(0, dotIndex); }
            var classBody = """
                    import java.io.*;
                    import java.nio.charset.*;
                    import java.nio.file.*;
                    import java.util.*;
                    import java.util.zip.*;
                    /** @version %s */
                    public final class %s {
                        public static void main(String[] args) throws IOException {
                            java.util.stream.Stream.of(null %s
                            ).skip(1).forEach(file -> write(file));
                        }
                        public static void write(File file) {
                            try {
                                var path = Path.of(file.path);
                                if (path.getParent() != null) Files.createDirectories(path.getParent());
                                var base64is = new ByteArrayInputStream(file.base64Body.getBytes(StandardCharsets.US_ASCII));
                                var is = new InflaterInputStream(Base64.getDecoder().wrap(base64is), new Inflater());
                                try (var os = Files.newOutputStream(path)) {
                                    var buffer = new byte[1024];
                                    var length = 0;
                                    while ((length = is.read(buffer)) != -1) { os.write(buffer, 0, length); }
                                }
                                System.out.println("Restored: " + path);
                            } catch (IOException e) {
                                throw new IllegalArgumentException("Failed to extract file: " + file.path, e);
                            }
                        }
                        record File(String path, String base64Body) {}
                    }
                    """.formatted(LocalDateTime.now(), cFile, splitSequence, "%s")
                    .split(splitSequence);

            try (var os = new PrintStream(new BufferedOutputStream(Files.newOutputStream(classFile)) , false, StandardCharsets.UTF_8)) {
                os.print(classBody[0]);
                for (var file : files) {
                    os.print("\n\t\t, new File(\"");
                    os.print(file.toString().replace('\\', '/'));
                    os.print("\", \"");
                    os.print(Base64.getEncoder().encodeToString(compress(Files.readAllBytes(file))));
                    os.print("\")");
                }
                os.print(classBody[1]);
            }
        }
        public byte[] compress(byte[] data) throws IOException {
            var baos = new ByteArrayOutputStream();
            try (var dos = new DeflaterOutputStream(baos, new Deflater())) {
                dos.write(data);
            }
            return baos.toByteArray();
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