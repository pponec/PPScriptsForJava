// Running by Java 17: $ java DirectoryBookmarks.java
// Licence: Apache License, Version 2.0, https://github.com/pponec/
// Enable PowerShell in Windows 11: Set-ExecutionPolicy unrestricted

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

/**
 * Manages a collection of directory bookmarks, enabling users to add, remove,
 * and retrieve bookmarked directories. This class provides functionalities to
 * handle bookmarks efficiently and supports operations such as loading from
 * and saving to a persistent storage.
 *
 * @author https://github.com/pponec
 * @version 2025-04-10
 */
public final class DirectoryBookmarks {
    static final String USER_HOME = System.getProperty("user.home");

    final String homePage = "https://github.com/pponec/PPScriptsForJava";
    final String appName = getClass().getSimpleName();
    final String appVersion = "2.0.1";
    final String requiredJavaModules = "java.base,java.net.http,jdk.compiler,jdk.crypto.ec";
    final char cellSeparator = '\t';
    final char comment = '#';
    final String newLine = System.lineSeparator();
    final String dataHeader = "%s %s %s (%s)".formatted(comment, appName, appVersion, homePage);
    final String currentDir = System.getProperty("user.dir");
    final String currentDirMark = ".";
    /** Shortcut for a home directory. Empty text is ignored. */
    final String homeDirMark = "~";
    final Class<?> mainClass = getClass();
    final String sourceUrl = "https://raw.githubusercontent.com/pponec/PPScriptsForJava/%s/src/main/java/net/ponec/script/%s.java"
            .formatted(true ? "main" : "development", appName);
    final File storeName;
    final PrintStream out;
    final PrintStream err;
    final boolean exitByException;
    final boolean isSystemWindows;
    final char dirSeparator;
    final Utilities utils = new Utilities();

    public static void main(String[] arguments) throws Exception {
        var args = List.of(arguments);
        var enforcedLinux = args.getFirst("").equals("linux");
        if (enforcedLinux) {
            args.remove(0);
        }
        new DirectoryBookmarks(new File(USER_HOME, ".directory-bookmarks.csv"),
                System.out,
                System.err, enforcedLinux, false).mainRun(args);
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
    public void mainRun(List<String> args) throws Exception {
        final var statement = args.getFirst("");
        if (statement.isEmpty()) printHelpAndExit(0);
        switch (statement.charAt(0) == '-' ? statement.substring(1) : statement) {
            case "l", "list" -> { // list all directories or show the one directory
                if (args.get(1, "").length() > 0) {
                    var defaultDir = "Bookmark [%s] has no directory.".formatted(args.get(1));
                    var dir = getDirectory(args.get(1), defaultDir);
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
                var key = args.get(1, homeDirMark);
                mainRun(List.of("l", key));
            }
            case "s", "save" -> {
                if (args.size() < 3) printHelpAndExit(-1);
                var msg = args.stream().skip(3).toList();
                save(args.get(1), args.get(2), msg); // (dir, key, comments)
            }
            case "d", "delete" -> {
                if (args.size() < 2) printHelpAndExit(-1);
                save("", args.get(1), List.of()); // (emptyDir, key, comments)
            }
            case "r", "remove" -> {
                if (args.size() < 2) printHelpAndExit(-1);
                removeBookmark(args.get(1));
            }
            case "b", "bookmarks"-> {
                var dir = args.get(1, currentDir);
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
                out.printf("Arguments are not supported: %s", String.join(" ", args));
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
        var executable = "java %s%s.%s".formatted(
                isJar ? "-jar " : "",
                appName,
                isJar ? "jar" : "java");
        out.printf("%s %s (%s)%n", appName, appVersion, homePage);
        out.printf("Usage: %s [slgdrbfuc] directory bookmark optionalComment%n", executable);
        if (isSystemWindows) {
            var initFile = "$HOME\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1";
            out.printf("Integrate the script to Windows: %s i >> %s", executable, initFile);
        } else {
            var initFile = "~/.bashrc";
            out.printf("Integrate the script to Ubuntu: %s i >> %s && . %s%n", executable, initFile, initFile);
        }
        exit(status);
    }

    /** Exit the application
     * @param status The positive value signs a correct terminate.
     */
    private void exit(int status, String... messageLines) {
        final var msg = String.join(newLine, messageLines);
        if (exitByException && status != 0) {
            throw new UnsupportedOperationException(msg);
        } else {
            final var output = status >= 0 ? this.out : this.err;
            output.println(msg);
            System.exit(status);
        }
    }

    private void printDirectories() throws IOException {
        var storeFile = createStoreFile();
        try (var reader = new BufferedReader(new FileReader(storeFile))) {
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
            case homeDirMark:
                return USER_HOME;
            default:
                var idx = Math.max(key.indexOf('/'), key.indexOf('\\'));
                var extKey = (idx >= 0 ? key.substring(0, idx) : key) + cellSeparator;
                var storeFile = createStoreFile();
                try (var reader = new BufferedReader(new FileReader(storeFile))) {
                    var dir = reader.lines()
                            .filter(line -> !line.startsWith(String.valueOf(comment)))
                            .filter(line -> line.startsWith(extKey))
                            .map(line -> line.substring(extKey.length()))
                            .findFirst();
                    if (dir.isPresent()) {
                        var dirString = dir.get();
                        var commentPattern = Pattern.compile("\\s+" + comment + "\\s");
                        var commentMatcher = commentPattern.matcher(dirString);
                        var endDir = idx >= 0 ? key.substring(idx) : "";
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
        save("", key, List.of());
    }

    private void save(String dir, String key, Collection<String> comments) throws IOException {
        if (key.indexOf(cellSeparator) >= 0 || key.indexOf(dirSeparator) >= 0) {
            exit(-1, "The bookmark contains a tab or a slash: '%s'".formatted(key));
        }
        if (currentDirMark.equals(dir)) {
            dir = currentDir;
        }
        var extendedKey = key + cellSeparator;
        var tempFile = getTempStoreFile();
        var storeFile = createStoreFile();
        try (var writer = new BufferedWriter(new FileWriter(tempFile))) {
            writer.append(dataHeader).append(newLine);
            if (!dir.isEmpty()) {
                // Function `isSystemMsWindows()` is required due a GitBash
                writer.append(key).append(cellSeparator).append(convertDir(true, dir, utils.isSystemMsWindows()));
                if (!comments.isEmpty()) {
                    writer.append(cellSeparator).append(comment);
                    for (String comment : comments) {
                        writer.append(' ').append(comment);
                    }
                }
                writer.append(newLine);
            }
            try (var reader = new BufferedReader(new FileReader(storeFile))) {
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
        return File.createTempFile(".dirbook", ".temp", storeName.getParentFile());
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
        try (var reader = new BufferedReader(new FileReader(createStoreFile()))) {
            result = reader.lines()
                    .filter(line -> !line.startsWith(String.valueOf(comment)))
                    .sorted()
                    .map(line -> line.substring(0, line.indexOf(cellSeparator)))
                    .toList();
        }
        return List.of(result);
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
        try (var reader = new BufferedReader(new FileReader(utils.getSrcPath()))) {
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
        var exePath = utils.getPathOfRunningApplication().replace(USER_HOME, "$HOME");
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
            var arguments = List.of(jarExe, "cfe", jarFile, appName);
            arguments.addAll(classFiles);
            var process = new ProcessBuilder(arguments)
                    .directory(new File(classFiles.get(0)).getParentFile())
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
                var path = location.toString();
                if (isSystemWindows && path.startsWith(protocol)) {
                    path = path.substring(protocol.length());
                } else {
                    path = location.getPath();
                }
                return URLDecoder.decode(path, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "%s.%s".formatted(appName, "java");
            }
        }

        private void deleteClasses(List<String> classFiles) {
            classFiles.stream().forEach(f -> {
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

        private List<String> getAllClassFiles(Class<?> mainClass) {
            final var result = new ArrayList<String>();
            final var suffix = ".class";
            result.add(mainClass.getSimpleName() + suffix);
            Stream.of(mainClass.getDeclaredClasses())
                    .map(c -> mainClass.getSimpleName() + '$' + c.getSimpleName() + suffix)
                    .forEach(result::add);
            return List.of(result);
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
            var script = Files.readString(fullJavaClass);
            script = script.replaceFirst(packageRegexp, "");
            Files.writeString(fullJavaClass, script);
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

        public T getFirst(T defaultValue) {
            return get(0, defaultValue);
        }

        public T getLast(T defaultValue) {
            return get(-1, defaultValue);
        }

        public static <T> List<T> of(T... items) {
            return new List<T>(Arrays.asList(items));
        }

        public static <T> List<T> of(Collection<T> items) {
            return new List<T>(items);
        }

        public boolean hasLength() {
            return size() > 0;
        }
    }

}