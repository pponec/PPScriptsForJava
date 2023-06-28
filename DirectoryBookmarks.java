// package net.ponec.kotlin.utils.script1;
// Java script converted from Kotlin by the GPTChat

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

public class DirectoryBookmarks {

    private static final String homePage = "https://github.com/pponec/DirectoryBookmarks";
    private static final String appName = "directory-bookmarks.kts";
    private static final String appVersion = "1.3";
    private static final String storeName = ".directory-bookmarks.csv";
    private static final char separator = '\t';
    private static final char comment = '#';
    private static final String newLine = System.lineSeparator();
    private static final String header = comment + " A directory bookmarks for the '" + appName + "' script";
    private static final String homeDir = System.getProperty("user.home");

    public static void main(String[] args) {
        if (args.length == 0)
            printHelpAndExit();
        switch (args[0]) {
            case "r":
                if (args.length < 2)
                    printHelpAndExit();
                String dir = getDirectory(args[1], " " + args[1] + " [bookmark] ");
                System.out.println(dir);
                break;
            case "w":
                if (args.length < 3)
                    printHelpAndExit();
                String[] msg = Arrays.copyOfRange(args, 3, args.length);
                save(args[1], args[2], msg);
                break;
            case "d":
                if (args.length < 1)
                    printHelpAndExit();
                delete(args[1]);
                break;
            case "l":
                printDirectories();
                break;
            case "i":
                printInstall(false);
                break;
            case "i4j":
                printInstall(true);
                break;
            default:
                printHelpAndExit();
        }
    }

    private static void printHelpAndExit() {
        String bashrc = "~/.bashrc";
        System.out.println("Script '" + appName + "' v" + appVersion + " (" + homePage + ")");
        System.out.println("Usage version: " + appName + " [rwl] bookmark directory optionalComment");
        System.out.println("Integrate the script to Ubuntu: " + appName + " i >> " + bashrc + " && . " + bashrc);
        System.exit(1);
    }

    private static void printDirectories() {
        File storeFile = getStoreFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(storeFile))) {
            reader.lines()
                    .filter(line -> !line.startsWith(String.valueOf(comment)))
                    .sorted()
                    .forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getDirectory(String key, String defaultDir) {
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
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return defaultDir;
        }
    }

    private static void delete(String key) {
        save(key, "");
    }

    private static void save(String dir, String key, String... comments) {
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            Files.move(tempFile.toPath(), storeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static File getStoreFile() {
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

    private static File getStoreFileTemplate() {
        try {
            return File.createTempFile("storeName", "", new File(homeDir));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void printInstall(boolean forJava) {
        String exec = forJava ?
                "java ~/bin/DirectoryBookmarks.java" :
                appName;
        String msg = ""
                + "# Shortcuts for " + appName + " utilities:\n"
                + "alias directoryBookmarksExe='" + exec + "'\n"
                + "cdf() { cd \"$(directoryBookmarksExe r \"$1\")\"; }\n"
                + "sdf() { directoryBookmarksExe w \"$PWD\" \"$@\"; }\n"
                + "ldf() { directoryBookmarksExe l; }\n";
        System.out.println(msg);
    }
}
