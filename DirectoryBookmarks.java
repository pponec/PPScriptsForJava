// Running by Java 17: $ java DirectoryBookmarks.java
// Licence: Apache License, Version 2.0, https://github.com/pponec/

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

public class DirectoryBookmarks {

    private final String homePage = "https://github.com/pponec/DirectoryBookmarks";
    private final String appName = getClass().getSimpleName();
    private final String appVersion = "1.8.2";
    private final String requiredJavaModules = "java.base,java.net.http,jdk.compiler";
    private final char cellSeparator = '\t';
    private final char dirSeparator = File.separatorChar;
    private final char comment = '#';
    private final String newLine = System.lineSeparator();
    private final String header = "%s A directory bookmarks for the '%s' script".formatted(comment, appName);
    private final String currentDir = System.getProperty("user.dir");
    private final String currentDirMark = ".";
    private final Class<?> mainClass = getClass();
    private final String sourceUrl = "https://raw.githubusercontent.com/pponec/DirectoryBookmarks/%s/%s.java"
            .formatted(!true ? "main" : "development", appName);
    private final File storeName;
    private final PrintStream out;

    public static void main(String[] args) throws Exception {
        new DirectoryBookmarks(
                new File(System.getProperty("user.home"), ".directory-bookmarks.csv"),
                System.out).start(args);
    }

    protected DirectoryBookmarks(File storeName, PrintStream out) {
        this.storeName = storeName;
        this.out = out;
    }

    /** The main object method */
    public void start(String... args) throws Exception {
        if (args.length == 0 || args[0].isEmpty()) printHelpAndExit();
        switch (args[0].charAt(args[0].length() - 1)) { // get the last character
            case 'l' -> { // list all directories or find one directory
                if (args.length > 1 && !args[1].isEmpty()) {
                    var dir = getDirectory(args[1], " %s [bookmark] ".formatted(args[1]));
                    out.println(dir);
                } else {
                    printDirectories();
                }
            }
            case 's' -> {
                if (args.length < 3) printHelpAndExit();
                var msg = Arrays.copyOfRange(args, 3, args.length);
                save(args[1], args[2], msg); // (dir, key, comments)
            }
            case 'r' -> {
                if (args.length < 2) printHelpAndExit();
                removeBookmark(args[1]);
            }
            case 'b'-> {
                var dir = args.length > 1 ? args[1] : currentDir;
                printAllBookmarksOfDirectory(dir);
            }
            case 'i'-> {
                printInstall();
            }
            case 'f'-> {
                fixMarksOfMissingDirectories();
            }
            case 'c' -> {
                compile();
            }
            case 'u' -> { // update
                download();
                if (isJar() && !isSystemWindows()) {
                    compile();
                    out.printf("Version %s was downloaded and compiled%n", appVersion);
                } else {
                    out.printf("Version %s was downloaded%n", appVersion);
                }
            }
            case 'v'-> {
                var scriptVersion = getScriptVersion();
                if (appVersion.equals(scriptVersion)) {
                    out.println(getScriptVersion());
                } else {
                    out.printf("%s -> %s".formatted(scriptVersion, appVersion));
                }
            }
            default -> {
                out.printf("Arguments are not supported: %s", String.join(" ", args));
                printHelpAndExit();
            }
        }
    }

    private void printHelpAndExit() {
        var isJar = isJar();
        var javaExe = "java %s%s.%s".formatted(
                isJar ? "-jar " : "",
                appName,
                isJar ? "jar" : "java");
        var bashrc = "~/.bashrc";
        out.printf("Script '%s' v%s (%s)%n", appName, appVersion, homePage);
        out.printf("Usage: %s [lsrbfuc] bookmark directory optionalComment%n", javaExe);
        out.printf("Integrate the script to Ubuntu: %s i >> %s && . %s%n", javaExe, bashrc, bashrc);
        System.exit(1);
    }

    private void printDirectories() throws IOException {
        var storeFile = createStoreFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(storeFile))) {
            reader.lines()
                    .filter(line -> !line.startsWith(String.valueOf(comment)))
                    .sorted()
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
                        var endDir = idx >= 0 ? "" + dirSeparator + key.substring(idx + 1) : "";
                        return (commentMatcher.find()
                                ? dirString.substring(0, commentMatcher.start())
                                : dirString)
                                + endDir;
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                return defaultDir;
        }
    }

    private void removeBookmark(String key) throws IOException {
        save("", key);
    }

    private void save(String dir, String key, String... comments) throws IOException {
        if (key.indexOf(cellSeparator) >= 0 || key.indexOf(dirSeparator) >= 0) {
            throw new IllegalArgumentException("the key contains a tab or a slash");
        }
        if (currentDirMark.equals(dir)) {
            dir = currentDir;
        }
        var extendedKey = key + cellSeparator;
        var tempFile = getTempStoreFile();
        var storeFile = createStoreFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            writer.append(header).append(newLine);
            if (!dir.isEmpty()) {
                writer.append(key).append(cellSeparator).append(dir);
                if (comments.length > 0) {
                    writer.append(cellSeparator).append(comment);
                    for (String comment : comments) {
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

    /** Read version from the external script. */
    private String getScriptVersion() {
        final var pattern = Pattern.compile("String\\s+appVersion\\s*=\\s*\"(.+)\"\\s*;");
        try (BufferedReader reader = new BufferedReader(new FileReader(getSrcPath()))) {
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
        var exePath = getPathOfRunningApplication();
        var javaHome = System.getProperty("java.home");
        if (isSystemWindows()) {
            var exe = "\"%s\\bin\\java\" --limit-modules %s %s\"%s\""
                    .formatted(javaHome, requiredJavaModules, isJar() ? "-jar " : "", exePath);
            var msg = String.join(System.lineSeparator(), ""
                    , "# Shortcuts for %s v%s utilities - for the PowerShell:".formatted(appName, appVersion)
                    , "function directoryBookmarks { & %s $args }".formatted(exe)
                    , "function cdf { Set-Location -Path $(directoryBookmarks -l $args) }"
                    , "function sdf { directoryBookmarks s . $args }"
                    , "function ldf { directoryBookmarks l $args }");
            out.println(msg);
        } else {
            var exe = "\"%s/bin/java\" --limit-modules %s %s\"%s\""
                    .formatted(javaHome, requiredJavaModules, isJar() ? "-jar " : "", exePath);
            var msg = String.join(System.lineSeparator(), ""
                    , "# Shortcuts for %s v%s utilities - for the Bash:".formatted(appName, appVersion)
                    , "alias directoryBookmarks='%s'".formatted(exe)
                    , "cdf() { cd \"$(directoryBookmarks l $1)\"; }"
                    , "sdf() { directoryBookmarks s %s \"$@\"; }".formatted(currentDirMark)
                    , "ldf() { directoryBookmarks l \"$1\"; }");
            out.println(msg);
        }
    }

    //  ~ ~ ~ ~ ~ ~ ~ UTILITIES ~ ~ ~ ~ ~ ~ ~

    /** Compile the script and build it to the executable JAR file */
    private void compile() throws Exception {
        var scriptDir = getScriptDir();
        var jarExe = "%s/bin/jar".formatted(System.getProperty("java.home"));
        var jarFile = "%s.jar".formatted(appName);
        var fullJavaClass = "%s/%s.java".formatted(scriptDir, appName);
        var classFile = new File(scriptDir, "%s.class".formatted(appName));
        classFile.deleteOnExit();

        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java Compiler is available");
        }
        var error = new ByteArrayOutputStream();
        var result = compiler.run(null, null, new PrintStream(error), fullJavaClass);
        if (result != 0) {
            throw new IllegalStateException(error.toString());
        }

        // Build a JAR file:
        String[] arguments = {jarExe, "cfe", jarFile, appName, classFile.getName()};
        var process = new ProcessBuilder(arguments)
                .directory(classFile.getParentFile())
                .start();
        var err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(err);
        }
    }

    private String getScriptDir() {
        var exePath = getPathOfRunningApplication();
        return exePath.substring(0, exePath.lastIndexOf(appName) - 1);
    }

    private boolean isJar() {
        return getPathOfRunningApplication().toLowerCase(Locale.ENGLISH).endsWith(".jar");
    }

    /** Get a full path to this source Java file. */
    private String getSrcPath() {
        return "%s/%s.java".formatted(getScriptDir(), appName);
    }

    private String getPathOfRunningApplication() {
        final var protocol = "file:/";
        try {
            final var location = mainClass.getProtectionDomain().getCodeSource().getLocation();
            var result = location.toString();
            if (isSystemWindows() && result.startsWith(protocol)) {
                result = result.substring(protocol.length());
            } else {
                result = location.getPath();
            }
            return URLDecoder.decode(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "%s.%s".formatted(appName, "java");
        }
    }

    private boolean isSystemWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
    }
}