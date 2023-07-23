// package net.ponec.java.utils.script;
// Java script converted from Kotlin by the GPTChat
// Running by Java 11: $ java DirectoryBookmarks.java

import javax.tools.ToolProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;

public class DirectoryBookmarks {

    private final String homePage = "https://github.com/pponec/DirectoryBookmarks";
    private final String appName = getClass().getName();
    private final String appVersion = "1.5";
    private final String storeName = ".directory-bookmarks.csv";
    private final char separator = '\t';
    private final char comment = '#';
    private final String newLine = System.lineSeparator();
    private final String header = comment + " A directory bookmarks for the '" + appName + "' script";
    private final String homeDir = System.getProperty("user.home");

    public static void main(String[] args) throws Exception {
        final var o = new DirectoryBookmarks();
        if (args.length == 0)
            o.printHelpAndExit();
        switch (args[0]) {
            case "r":
                if (args.length < 2)
                    o.printHelpAndExit();
                String dir = o.getDirectory(args[1], " " + args[1] + " [bookmark] ");
                System.out.println(dir);
                break;
            case "w":
                if (args.length < 3)
                    o.printHelpAndExit();
                String[] msg = Arrays.copyOfRange(args, 3, args.length);
                o.save(args[2], args[1], msg);
                break;
            case "d":
                if (args.length < 2)
                    o.printHelpAndExit();
                o.delete(args[1]);
                break;
            case "l":
                if (args.length > 1 && !args[1].isEmpty()) {
                    System.out.println(o.getDirectory(args[1], ""));
                } else {
                    o.printDirectories();
                }
                break;
            case "i":
                o.printInstall(false);
                break;
            case "i4j":
                o.printInstall(true);
                break;
            case "e":
                o.removeAllDeprecatedDiredtories();
                break;
            case "c":
                o.compile();
                break;
            default:
                o.printHelpAndExit();
        }
    }

    private void printHelpAndExit() {
        String bashrc = "~/.bashrc";
        System.out.println("Script '" + appName + "' v" + appVersion + " (" + homePage + ")");
        System.out.println("Usage: java " + appName + ".java [rwldec] bookmark directory optionalComment");
        System.out.println("Integrate the script to Ubuntu: java " + appName + ".java i >> " + bashrc + " && . " + bashrc);
        System.exit(1);
    }

    private void printDirectories() throws IOException {
        File storeFile = getStoreFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(storeFile))) {
            reader.lines()
                    .filter(line -> !line.startsWith(String.valueOf(comment)))
                    .sorted()
                    .forEach(System.out::println);
        }
    }

    private String getDirectory(String key, String defaultDir) throws IOException {
        switch (key) {
            case "~":
                return homeDir;
            case ".":
                return key;
            default:
                String extendedKey = key + separator;
                File storeFile = getStoreFile();
                try (BufferedReader reader = new BufferedReader(new FileReader(storeFile))) {
                    Optional<String> dir = reader.lines()
                            .filter(line -> !line.startsWith(String.valueOf(comment)))
                            .filter(line -> line.startsWith(extendedKey))
                            .map(line -> line.substring(extendedKey.length()))
                            .findFirst();
                    if (dir.isPresent()) {
                        String dirString = dir.get();
                        Pattern commentPattern = Pattern.compile("\\s+" + comment + "\\s");
                        java.util.regex.Matcher commentMatcher = commentPattern.matcher(dirString);
                        if (commentMatcher.find()) {
                            return dirString.substring(0, commentMatcher.start());
                        } else {
                            return dirString;
                        }
                    }
                }
                return defaultDir;
        }
    }

    private void delete(String key) throws IOException {
        save(key, "");
    }

    private void save(String key, String dir, String... comments) throws IOException {
        if (key.contains(String.valueOf(separator))) {
            throw new IllegalArgumentException("the key contains a tab");
        }
        String extendedKey = key + separator;
        File tempFile = getStoreFileTemplate();
        File storeFile = getStoreFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            writer.write(header);
            writer.write(newLine);
            if (!dir.isEmpty()) {
                writer.write(key + separator + dir);
                if (comments.length > 0) {
                    writer.write("" + separator + comment);
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
        File result = new File(homeDir, storeName);
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
        var keys = Collections.<String>emptyList();
        try (BufferedReader reader = new BufferedReader(new FileReader(getStoreFile()))) {
            keys = reader.lines()
                    .filter(line -> !line.startsWith(String.valueOf(comment)))
                    .map(line -> line.substring(0, line.indexOf(separator)))
                    .toList();
        }
        keys.stream()
                .filter(key -> {
                    try {
                        var dir = getDirectory(key, "");
                        return dir.isEmpty() || !new File(dir).isDirectory();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
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

    /** Compile the script and build it to the executable JAR file */
    private void compile() throws Exception {
        var classFile = new File(appName + ".class");
        classFile.deleteOnExit();

        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java Compiler is available");
        }
        var error = new ByteArrayOutputStream();
        var result = compiler.run(null, null, new PrintStream(error), appName  + ".java");
        if (result != 0) {
            throw new IllegalStateException(error.toString());
        }

        // Build a JAR file:
        String[] arguments = {"jar", "cfe", appName + ".jar", appName, classFile.getName()};
        var process = new ProcessBuilder(arguments).start();
        var err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(err);
        }
    }

    private void printInstall(boolean forJar) {
        String exec = forJar
                ? String.format("java -jar ~/bin/%s.jar", appName)
                : String.format("java ~/bin/%s.java", appName);
        String msg = String.join("\n", ""
                , "# Shortcuts for " + appName + " utilities:"
                , "alias directoryBookmarksExe='" + exec + "'"
                , "cdf() { cd \"$(directoryBookmarksExe r \"$1\")\"; }"
                , "sdf() { directoryBookmarksExe w \"$PWD\" \"$@\"; }"
                , "ldf() { directoryBookmarksExe l \"$1\"; }");
        System.out.println(msg);
    }
}
