// package net.ponec.java.utils.script;
// Java script converted from Kotlin by the GPTChat
// Running by Java 17: $ java DirectoryBookmarks.java

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
    private final String appVersion = "1.7.3";
    private final String storeName = ".directory-bookmarks.csv";
    private final char cellSeparator = '\t';
    private final char dirSeparator = File.separatorChar;
    private final char comment = '#';
    private final String newLine = System.lineSeparator();
    private final String header = comment + " A directory bookmarks for the '" + appName + "' script";
    private final String homeDir = System.getProperty("user.home");
    private final String currentDir = System.getProperty("user.dir");
    private final String currentDirMark = "";
    private final Class<?> mainClass = getClass();
    private final DirectoryBookmarks utils = this; // In the future, move the instruments to a separate class.
    private final String sourceUrl = "https://raw.githubusercontent.com/pponec/DirectoryBookmarks/%s/%s.java"
            .formatted(!true ? "main" : "development", appName);

    public static void main(String[] args) throws Exception {
        final var o = new DirectoryBookmarks();
        if (args.length == 0)
            o.printHelpAndExit();
        switch (args[0]) {
            case "r": // read directory
                if (args.length > 1 && !args[1].isEmpty()) {
                    var dir = o.getDirectory(args[1], " %s [bookmark] ".formatted(args[1]));
                    System.out.println(dir);
                } else {
                    o.printDirectories();
                }
                break;
            case "s":
                if (args.length < 3)
                    o.printHelpAndExit();
                var msg = Arrays.copyOfRange(args, 3, args.length);
                o.save(args[2], args[1], msg);
                break;
            case "d":
                if (args.length < 2)
                    o.printHelpAndExit();
                o.delete(args[1]);
                break;
            case "k":
                var dir = args.length > 1 ? args[1] : o.currentDir;
                o.printAllKeysForDirectory(dir);
                break;
            case "i":
                o.printInstall();
                break;
            case "e":
                o.removeAllDeprecatedDiredtories();
                break;
            case "c":
                o.utils.compile();
                break;
            case "u": // update
                o.download();
                if (o.isJar()) {
                    o.compile();
                    System.out.println("Version %s was downloaded and compiled".formatted(o.appVersion));
                } else {
                    System.out.println("Version %s was downloaded".formatted(o.appVersion));
                }
                break;
            case "v":
                System.out.println(o.getVersion());
                break;
            default:
                System.out.println("Arguments are not supported: %s".formatted(String.join(" ", args)));
                o.printHelpAndExit();
        }
    }

    private void printHelpAndExit() {
        var isJar = utils.isJar();
        var javaExe = "java %s%s.%s".formatted(
                isJar ? "-jar " : "",
                appName,
                isJar ? "jar" : "java");
        var bashrc = "~/.bashrc";
        System.out.println("Script '%s' v%s (%s)".formatted(appName, appVersion, homePage));
        System.out.println("Usage: %s [rsdkecu] bookmark directory optionalComment".formatted(javaExe));
        System.out.println("Integrate the script to Ubuntu: %s i >> %s && . %s".formatted(javaExe, bashrc, bashrc));
        System.exit(1);
    }

    private void printDirectories() throws IOException {
        var storeFile = getStoreFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(storeFile))) {
            reader.lines()
                    .filter(line -> !line.startsWith(String.valueOf(comment)))
                    .sorted()
                    .forEach(System.out::println);
        }
    }

    /**
     * Find the directory or get a default value.
     * @param key The directory key can end with the name of the following subdirectory.
     *            by the example: {@code "key/directoryName"} .
     * @param defaultDir Default directory name.
     * @return
     */
    private String getDirectory(String key, String defaultDir) {
        switch (key) {
            case "~":
                return homeDir;
            case ".":
                return key;
            case currentDirMark:
                return currentDir;
            default:
                var idx = key.indexOf(dirSeparator);
                var extKey = (idx >= 0 ? key.substring(0, idx) : key) + cellSeparator;
                var storeFile = getStoreFile();
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
                        if (commentMatcher.find()) {
                            return dirString.substring(0, commentMatcher.start()) + endDir;
                        } else {
                            return dirString + endDir;
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                return defaultDir;
        }
    }

    private void delete(String key) throws IOException {
        save(key, "");
    }

    private void save(String key, String dir, String... comments) throws IOException {
        if (key.indexOf(cellSeparator) >= 0 || key.indexOf(dirSeparator) >= 0) {
            throw new IllegalArgumentException("the key contains a tab or a slash");
        }
        if (currentDirMark.equals(dir)) {
            dir = currentDir;
        }
        var extendedKey = key + cellSeparator;
        var tempFile = getStoreFileTemplate();
        var storeFile = getStoreFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            writer.write(header);
            writer.write(newLine);
            if (!dir.isEmpty()) {
                writer.write(key + cellSeparator + dir);
                if (comments.length > 0) {
                    writer.write("" + cellSeparator + comment);
                    for (String comment : comments) {
                        writer.append(" ").append(comment);
                    }
                }
                writer.write(newLine);
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(storeFile))) {
                reader.lines()
                        .filter(line -> !line.startsWith(String.valueOf(comment)))
                        .filter(line -> !line.startsWith(extendedKey))
                        .forEach(line -> {
                            try {
                                writer.write(line);
                                writer.write(newLine);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }
        }
        Files.move(tempFile.toPath(), storeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private File getStoreFile() {
        var result = new File(homeDir, storeName);
        if (!result.isFile()) {
            try {
                result.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private File getStoreFileTemplate() throws IOException {
        return File.createTempFile("storeName", "", new File(homeDir));
    }

    private void removeAllDeprecatedDiredtories() throws IOException {
        var keys = getAllSortedKeys();
        keys.stream()
                .filter(key -> {
                    var dir = getDirectory(key, "");
                    return dir.isEmpty() || !new File(dir).isDirectory();
                })
                .forEach(key -> {
                    try {
                        var msg = "Removed: %s\t%s".formatted(key, getDirectory(key, "?"));
                        System.out.println(msg);
                        delete(key);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    private List<String> getAllSortedKeys() throws IOException {
        var result = Collections.<String>emptyList();
        try (BufferedReader reader = new BufferedReader(new FileReader(getStoreFile()))) {
            result = reader.lines()
                    .filter(line -> !line.startsWith(String.valueOf(comment)))
                    .sorted()
                    .map(line -> line.substring(0, line.indexOf(cellSeparator)))
                    .toList();
        }
        return result;
    }

    private void printAllKeysForDirectory(String directory) throws IOException {
        getAllSortedKeys().forEach(key -> {
            if (directory.equals(getDirectory(key, ""))) {
                System.out.println(key);
            }
        });
    }

    private void download() throws IOException, InterruptedException {
        var srcPath = "%s/%s.java".formatted(utils.getScriptDir(), appName);
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            Files.writeString(Path.of(srcPath), response.body());
        } else {
            throw new IllegalStateException("Downloading error code: %s".formatted(response.statusCode()));
        }
    }

    /** Read version from the external script. */
    private String getVersion() throws IOException {
        final var pattern = Pattern.compile("String\\s+appVersion\\s*=\\s*\"(.+)\"\\s*;");
        final var srcPath = "%s/%s.java".formatted(utils.getScriptDir(), appName);
        try (BufferedReader reader = new BufferedReader(new FileReader(srcPath))) {
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
        var javaExe = "%s/bin/java".formatted(System.getProperty("java.home"));
        var applExe = utils.isJar()
                ? "%s -jar %s".formatted(javaExe, exePath)
                : "%s %s".formatted(javaExe, exePath);
        var msg = String.join("\n", ""
                , "# Shortcuts for %s v%s utilities:".formatted(appName, appVersion)
                , "alias directoryBookmarks='%s'".formatted(applExe)
                , "cdf() { cd \"$(directoryBookmarks r \"$1\")\"; }"
                , "sdf() { directoryBookmarks s \"%s\" \"$@\"; }".formatted(currentDirMark)
                , "ldf() { directoryBookmarks r \"$1\"; }");
        System.out.println(msg);
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

    private String getPathOfRunningApplication() {
        final var protocol = "file:/";
        final var windows = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
        try {
            final var location = mainClass.getProtectionDomain().getCodeSource().getLocation();
            var result = location.toString();
            if (windows && result.startsWith(protocol)) {
                result = result.substring(protocol.length());
            } else {
                result = location.getPath();
            }
            return URLDecoder.decode(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "$s.$s".formatted(appName, "java");
        }
    }
}
