// Running by Java 17: $ java DirectoryBookmarks.java
// Licence: Apache License, Version 2.0, https://github.com/pponec/

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
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class DirectoryBookmarks {
    private static final String USER_HOME = System.getProperty("user.home");

    private final String homePage = "https://github.com/pponec/DirectoryBookmarks";
    private final String appName = getClass().getSimpleName();
    private final String appVersion = "1.9.4";
    private final String requiredJavaModules = "java.base,java.net.http,jdk.compiler,jdk.crypto.ec";
    private final char cellSeparator = '\t';
    private final char comment = '#';
    private final String newLine = System.lineSeparator();
    private final String dataHeader = "%s %s %s (%s)".formatted(comment, appName, appVersion, homePage);
    private final String currentDir = System.getProperty("user.dir");
    private final String currentDirMark = ".";
    /** Shortcut for a home directory. Empty text is ignored. */
    private final String homeDirMark = "~";
    private final Class<?> mainClass = getClass();
    private final String sourceUrl = "https://raw.githubusercontent.com/pponec/DirectoryBookmarks/%s/src/main/java/net/ponec/script/%s.java"
            .formatted(!true ? "main" : "development", appName);
    private final File storeName;
    private final PrintStream out;
    private final PrintStream err;
    private final boolean exitByException;
    private final boolean isSystemWindows;
    private final char dirSeparator;
    private final Utilities utils = new Utilities();

    public static void main(String[] arguments) throws Exception {
        var args = Array.of(arguments);
        var enforcedLinux = args.getFirst().orElse("").equals("linux");
        if (enforcedLinux) {
            args = args.removeFirst();
        }
        new DirectoryBookmarks(new File(USER_HOME, ".directory-bookmarks.csv"),
                System.out,
                System.err, enforcedLinux, false).start(args);
    }

    DirectoryBookmarks(File storeName,
                       PrintStream out,
                       PrintStream err,
                       boolean enforcedLinux,
                       boolean exitByException) {
        this.storeName = storeName;
        this.out = out;
        this.err = err;
        this.exitByException = exitByException;
        this.isSystemWindows = !enforcedLinux && utils.isSystemMsWindows();
        this.dirSeparator = enforcedLinux ? '/' : File.separatorChar;
    }

    /** The main object method */
    public void start(Array<String> args) throws Exception {
        final var statement = args.getFirst().orElse("");
        if (statement.isEmpty()) printHelpAndExit(0);
        switch (statement.charAt(0) == '-' ? statement.substring(1) : statement) {
            case "l", "list" -> { // list all directories or show the one directory
                if (args.get(1).orElse("").length() > 0) {
                    var defaultDir = "Bookmark [%s] has no directory.".formatted(args.getItem(1));
                    var dir = getDirectory(args.getItem(1), defaultDir);
                    if (dir == defaultDir) {
                        exit(-1, defaultDir);
                    } else {
                        out.println(dir);
                    }
                } else {
                    printDirectories();
                }
            }
            case "g", "get" -> { // get only one directory, default is the home.
                var key = args.get(1).orElse(homeDirMark);
                start(Array.of("l", key));
            }
            case "s", "save" -> {
                if (args.size() < 3) printHelpAndExit(-1);
                var msg = args.subArray(3);
                save(args.getItem(1), args.getItem(2), msg); // (dir, key, comments)
            }
            case "r", "read" -> {
                if (args.size() < 2) printHelpAndExit(-1);
                removeBookmark(args.getItem(1));
            }
            case "b", "bookmarks"-> {
                var dir = args.get(1).orElse(currentDir);
                printAllBookmarksOfDirectory(dir);
            }
            case "i", "install"-> {
                printInstall();
            }
            case "f", "fix"-> {
                fixMarksOfMissingDirectories();
            }
            case "c", "compile" -> {
                utils.compile();
            }
            case "u", "upgrade" -> { // update
                utils.download();
                out.printf("%s %s was downloaded. The following compilation is recommended.%n",
                        appName, getScriptVersion());
            }
            case "v", "version"-> {
                var scriptVersion = getScriptVersion();
                if (appVersion.equals(scriptVersion)) {
                    out.println(scriptVersion);
                } else {
                    out.printf("%s -> %s%n".formatted(scriptVersion, appVersion));
                }
            }
            default -> {
                out.printf("Arguments are not supported: %s", String.join(" ", args.toList()));
                printHelpAndExit(-1);
            }
        }
    }

    /**
     * Print a default help and exit the application.
     * @param status The positive value signs a correct terminate.
     */
    private void printHelpAndExit(int status) {
        var out = status == 0 ? this.out : this.err;
        var isJar = utils.isJar();
        var javaExe = "java %s%s.%s".formatted(
                isJar ? "-jar " : "",
                appName,
                isJar ? "jar" : "java");
        out.printf("%s %s (%s)%n", appName, appVersion, homePage);
        out.printf("Usage: %s [lgsrbfuc] bookmark directory optionalComment%n", javaExe);
        if (isSystemWindows) {
            var initFile = "$HOME\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1";
            out.printf("Integrate the script to Windows: %s i >> %s", javaExe, initFile);
        } else {
            var initFile = "~/.bashrc";
            out.printf("Integrate the script to Ubuntu: %s i >> %s && . %s%n", javaExe, initFile, initFile);
        }
        exit(status);
    }

    /** Exit the application
     * @param status The positive value signs a correct terminate.
     */
    private void exit(int status, String... messageLines) {
        final var msg = String.join(newLine, messageLines);
        if (exitByException && status < 0) {
            throw new UnsupportedOperationException(msg);
        } else {
            final var output = status >= 0 ? this.out : this.err;
            output.println(msg);
            System.exit(status);
        }
    }

    private void printDirectories() throws IOException {
        var storeFile = createStoreFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(storeFile))) {
            reader.lines()
                    .filter(line -> !line.startsWith(String.valueOf(comment)))
                    .sorted()
                    .map(line -> isSystemWindows ? line.replace('/', '\\') : line)
                    .forEach(out::println);
        }
    }

    /**
     * Find the directory or get a default value.
     * @param key The directory key can end with the name of the following subdirectory.
     *            by the example: {@code "key/directoryName"} .
     * @param defaultDir Default directory name.
     */
    private String getDirectory(String key, String defaultDir) {
        switch (key) {
            case currentDirMark:
                return currentDir;
            default:
                var idx = key.indexOf(dirSeparator);
                var extKey = (idx >= 0 ? key.substring(0, idx) : key) + cellSeparator;
                var storeFile = createStoreFile();
                try (BufferedReader reader = new BufferedReader(new FileReader(storeFile))) {
                    var dir = reader.lines()
                            .filter(line -> !line.startsWith(String.valueOf(comment)))
                            .filter(line -> line.startsWith(extKey))
                            .map(line -> line.substring(extKey.length()))
                            .findFirst();
                    if (dir.isPresent()) {
                        var dirString = dir.get();
                        var commentPattern = Pattern.compile("\\s+" + comment + "\\s");
                        var commentMatcher = commentPattern.matcher(dirString);
                        var endDir = idx >= 0 ? dirSeparator + key.substring(idx + 1) : "";
                        var result = (commentMatcher.find()
                                ? dirString.substring(0, commentMatcher.start())
                                : dirString)
                                + endDir;
                        return convertDir(false, result, isSystemWindows);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                return defaultDir;
        }
    }

    private void removeBookmark(String key) throws IOException {
        save("", key, Array.of());
    }

    private void save(String dir, String key, Array<String> comments) throws IOException {
        if (key.indexOf(cellSeparator) >= 0 || key.indexOf(dirSeparator) >= 0) {
            exit(-1, "The bookmark contains a tab or a slash: '%s'".formatted(key));
        }
        if (currentDirMark.equals(dir)) {
            dir = currentDir;
        }
        var extendedKey = key + cellSeparator;
        var tempFile = getTempStoreFile();
        var storeFile = createStoreFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            writer.append(dataHeader).append(newLine);
            if (!dir.isEmpty()) {
                // Function `isSystemMsWindows()` is required due a GitBash
                writer.append(key).append(cellSeparator).append(convertDir(true, dir, utils.isSystemMsWindows()));
                if (comments.hasLength()) {
                    writer.append(cellSeparator).append(comment);
                    for (String comment : comments.toList()) {
                        writer.append(' ').append(comment);
                    }
                }
                writer.append(newLine);
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(storeFile))) {
                reader.lines()
                        .filter(line -> !line.startsWith(String.valueOf(comment)))
                        .filter(line -> !line.startsWith(extendedKey))
                        .sorted()
                        .forEach(line -> {
                            try {
                                writer.append(line).append(newLine);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }
        Files.move(tempFile.toPath(), storeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private File createStoreFile() {
        if (!storeName.isFile()) {
            try {
                storeName.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException(storeName.toString(), e);
            }
        }
        return storeName;
    }

    private File getTempStoreFile() throws IOException {
        return File.createTempFile(".dirbook", "", storeName.getParentFile());
    }

    private void fixMarksOfMissingDirectories() throws IOException {
        var keys = getAllSortedKeys();
        keys.stream()
                .filter(key -> {
                    var dir = getDirectory(key, "");
                    return dir.isEmpty() || !new File(dir).isDirectory();
                })
                .forEach(key -> {
                    try {
                        var msg = "Removed: %s\t%s".formatted(key, getDirectory(key, "?"));
                        out.println(msg);
                        removeBookmark(key);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    private List<String> getAllSortedKeys() throws IOException {
        var result = Collections.<String>emptyList();
        try (BufferedReader reader = new BufferedReader(new FileReader(createStoreFile()))) {
            result = reader.lines()
                    .filter(line -> !line.startsWith(String.valueOf(comment)))
                    .sorted()
                    .map(line -> line.substring(0, line.indexOf(cellSeparator)))
                    .toList();
        }
        return result;
    }

    private void printAllBookmarksOfDirectory(String directory) throws IOException {
        getAllSortedKeys().forEach(key -> {
            if (directory.equals(getDirectory(key, ""))) {
                out.println(key);
            }
        });
    }

    /** Read version from the external script. */
    private String getScriptVersion() {
        final var pattern = Pattern.compile("String\\s+appVersion\\s*=\\s*\"(.+)\"\\s*;");
        try (BufferedReader reader = new BufferedReader(new FileReader(utils.getSrcPath()))) {
            return reader.lines()
                    .map(line ->  {
                        final var matcher = pattern.matcher(line);
                        return matcher.find() ? matcher.group(1) : null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(appVersion);
        } catch (Exception e) {
            return appVersion;
        }
    }

    private void printInstall() {
        var exePath = utils.getPathOfRunningApplication();
        var javaHome = System.getProperty("java.home");
        if (isSystemWindows) {
            var exe = "\"%s\\bin\\java\" --limit-modules %s %s\"%s\""
                    .formatted(javaHome, requiredJavaModules, utils.isJar() ? "-jar " : "", exePath);
            var msg = String.join(System.lineSeparator(), ""
                    , "# Shortcuts for %s v%s utilities - for the PowerShell:".formatted(appName, appVersion)
                    , "function directoryBookmarks { & %s $args }".formatted(exe)
                    , "function cdf { Set-Location -Path $(directoryBookmarks -g $args) }"
                    , "function sdf { directoryBookmarks s $($PWD.Path) @args }"
                    , "function ldf { directoryBookmarks l $args }"
                    , "function cpf() { cp ($args[0..($args.Length - 2)]) -Destination (ldf $args[-1]) -Force }");
            out.println(msg);
        } else {
            var exe = "\"%s/bin/java\" --limit-modules %s %s\"%s\""
                    .formatted(javaHome, requiredJavaModules, utils.isJar() ? "-jar " : "", exePath);
            var msg = String.join(System.lineSeparator(), ""
                    , "# Shortcuts for %s v%s utilities - for the Bash:".formatted(appName, appVersion)
                    , "alias directoryBookmarks='%s'".formatted(exe)
                    , "cdf() { cd \"$(directoryBookmarks g $1)\"; }"
                    , "sdf() { directoryBookmarks s \"$PWD\" \"$@\"; }" // Ready for symbolic links
                    , "ldf() { directoryBookmarks l \"$1\"; }"
                    , "cpf() { argCount=$#; cp ${@:1:$((argCount-1))} \"$(ldf ${!argCount})\"; }");
            out.println(msg);
        }
    }

    /** Convert a directory text to the store format or back */
    private String convertDir(boolean toStoreFormat, String dir, boolean isSystemWindows) {
        final var homeDirMarkEnabled = !homeDirMark.isEmpty();
        if (toStoreFormat) {
            var result = homeDirMarkEnabled && dir.startsWith(USER_HOME)
                    ? homeDirMark + dir.substring(USER_HOME.length())
                    : dir;
            return isSystemWindows
                    ? result.replace('\\', '/')
                    : result;
        } else {
            var result = isSystemWindows
                    ? dir.replace('/', '\\')
                    : dir;
            return homeDirMarkEnabled && result.startsWith(homeDirMark)
                    ? USER_HOME + result.substring(homeDirMark.length())
                    : result;
        }
    }

    //  ~ ~ ~ ~ ~ ~ ~ UTILITIES ~ ~ ~ ~ ~ ~ ~

    class Utilities {

        /** Compile the script and build it to the executable JAR file */
        private void compile() throws Exception {
            if (isJar()) {
                exit(-1, "Use the statement rather: java %s.java c".formatted(appName));
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
            final var protocol = "file:/";
            try {
                final var location = mainClass.getProtectionDomain().getCodeSource().getLocation();
                var result = location.toString();
                if (isSystemWindows && result.startsWith(protocol)) {
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
            System.out.println("packageRegexp: "  + packageRegexp);
            var script = Files.readString(fullJavaClass);
            script = script.replaceFirst(packageRegexp, "");
            Files.writeString(fullJavaClass, script);
        }
    }

    /** The immutable Array wrapper (from the Ujorm framework) */
    public record Array<T>(T[] array) {

        /** Negative index is supported */
        public Optional<T> get(final int i) {
            final var j = i >= 0 ? i : array.length + i;
            return Optional.ofNullable(j >= 0 && j < array.length ? array[j] : null);
        }

        /** Add new items to the new Array */
        @SuppressWarnings("unchecked")
        public Array<T> add(final T... toAdd) {
            final T[] result = Arrays.copyOf(array, array.length + toAdd.length);
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
            final var result = java.lang.reflect.Array.newInstance(type, array.length);
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
            return new Array<>(chars);
        }
    }
}