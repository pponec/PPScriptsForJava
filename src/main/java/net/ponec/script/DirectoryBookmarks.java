// Running by Java 17: $ java DirectoryBookmarks.java
// Licence: Apache License, Version 2.0, https://github.com/pponec/
// Enable PowerShell in Windows 11: Set-ExecutionPolicy unrestricted

package net.ponec.script;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;

public final class DirectoryBookmarks {
    static final String USER_HOME = System.getProperty("user.home");

    final String homePage = "https://github.com/pponec/PPScriptsForJava";
    final String appName = getClass().getSimpleName();
    final String appVersion = "2.0.0";
    final char cellSeparator = '\t';
    final char comment = '#';
    final String newLine = System.lineSeparator();
    final String dataHeader = "%s %s %s (%s)".formatted(comment, appName, appVersion, homePage);
    final String currentDir = System.getProperty("user.dir");
    final String currentDirMark = ".";
    /** Shortcut for a home directory. Empty text is ignored. */
    final String homeDirMark = "~";
    final String sourceUrl = "https://raw.githubusercontent.com/pponec/PPScriptsForJava/%s/src/main/java/net/ponec/script/%s.java"
            .formatted(true ? "main" : "development", appName);
    final File storeName;
    final PrintStream out;
    final PrintStream err;
    final boolean exitByException;
    final boolean isSystemWindows;
    final char dirSeparator;

    public static void main(String[] arguments) throws Exception {
        var args = MyList.of(arguments);
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
        this.isSystemWindows = !enforcedLinux && isSystemMsWindows();
        this.dirSeparator = enforcedLinux ? '/' : File.separatorChar;
    }

    /** The main object method */
    public void mainRun(MyList<String> args) throws Exception {
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
                mainRun(MyList.of("l", key));
            }
            case "s", "save" -> {
                if (args.size() < 3) printHelpAndExit(-1);
                var msg = args.stream().skip(3).toList();
                save(args.get(1), args.get(2), msg); // (dir, key, comments)
            }
            case "d", "delete" -> {
                if (args.size() < 2) printHelpAndExit(-1);
                save("", args.get(1), MyList.of()); // (emptyDir, key, comments)
            }
            case "r", "read" -> {
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
        var isJar = false;
        var javaExe = "java %s%s.%s".formatted(
                isJar ? "-jar " : "",
                appName,
                isJar ? "jar" : "java");
        out.printf("%s %s (%s)%n", appName, appVersion, homePage);
        out.printf("Usage: %s [slgdrbfuc] directory bookmark optionalComment%n", javaExe);
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
        save("", key, MyList.of());
    }

    private void save(String dir, String key, List<String> comments) throws IOException {
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
                writer.append(key).append(cellSeparator).append(convertDir(true, dir, isSystemMsWindows()));
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
        return appVersion;
    }

    private void printInstall() {
        out.println("todo");
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

    private boolean isSystemMsWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
    }

    /** An extended ArrayList class */
    public static final class MyList<T> extends ArrayList<T> {

        private MyList(final Collection<T> c) {
            super(c);
        }

        public T get(final int i, final T defaultValue) {
            final var size = size();
            final var j = i >= 0 ? i : size + i;
            final var result = j >= 0 && j < size
                    ? get(j)
                    : defaultValue;
            return result != null ? result : defaultValue;
        }

        public T getFirst(T defaultValue) {
            return get(0, defaultValue);
        }

        public T getLast(T defaultValue) {
            return get(size() - 1, defaultValue);
        }

        public static <T> MyList<T> of(T... items) {
            return new MyList<T>(Arrays.asList(items));
        }

        public static <T> MyList<T> of(Collection<T> items) {
            return new MyList<T>(items);
        }

        public boolean hasLength() {
            return size() > 0;
        }
    }

}