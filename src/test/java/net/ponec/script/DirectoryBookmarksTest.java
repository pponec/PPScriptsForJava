// The test must be compiled to the run.
// Licence: Apache License, Version 2.0, https://github.com/pponec/

package net.ponec.script;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class DirectoryBookmarksTest {

    private static final String homeDir = DirectoryBookmarks.USER_HOME;
    private static final Charset charset = StandardCharsets.UTF_8;

    @Test
    void mainRunTest() throws Exception {
        var ctx = DirBookContext.of();
        var instance = ctx.instance;
        instance.mainRun(list("save", "/test/dev", "bin", "My", "comment"));
        var bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/PPScriptsForJava)
                bin\t/test/dev	# My comment
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarkFile());
        assertEquals("", ctx.getOut());

        instance.mainRun(list("save", "/test/conf", "conf"));
        bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/PPScriptsForJava)
                conf\t/test/conf
                bin\t/test/dev	# My comment
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarkFile());
        assertEquals("", ctx.getOut());

        instance.mainRun(list("save", "/test/bin", "bin"));
        bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/PPScriptsForJava)
                bin\t/test/bin
                conf\t/test/conf
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarkFile());
        assertEquals("", ctx.getOut());
        assertEquals("", ctx.getErr());

        instance.mainRun(list("list", "bin"));
        assertEquals("/test/bin\n", ctx.getOut());
        assertEquals("", ctx.getErr());

        bookmarks = """
                bin\t/test/bin
                conf\t/test/conf
                """.trim();
        assertEquals(bookmarks, ctx.bookmarksString());
        assertEquals("", ctx.getErr());

        instance.mainRun(list("version"));
        assertEquals(instance.appVersion + "\n", ctx.getOut());

        instance.mainRun(list("delete", "conf"));
        bookmarks = "bin\t/test/bin";
        assertEquals(bookmarks, ctx.bookmarksString());
        assertEquals("", ctx.getErr());

        var ex = assertThrows(RuntimeException.class, () ->
                instance.mainRun(list("list", "conf")));
        assertEquals("Bookmark [conf] has no directory.", ex.getMessage());

        instance.mainRun(list("list", "~"));
        assertEquals(homeDir + "\n", ctx.getOut());
        assertEquals("", ctx.getErr());

        bookmarks = "bin\t/test/bin";
        assertEquals(bookmarks, ctx.bookmarksString());
    }

    @Test
    void getHomeTest() throws Exception {
        var ctx = DirBookContext.of();
        var instance = ctx.instance;
        instance.mainRun(list("save", homeDir + "/test/bin", "bin", "My", "comment"));

        var bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/PPScriptsForJava)
                bin\t~/test/bin	# My comment
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarkFile());
        assertEquals("", ctx.getOut());

        instance.mainRun(list("get"));
        assertEquals(homeDir + "\n", ctx.getOut());

        instance.mainRun(list("save", "~", "myHome"));
        bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/PPScriptsForJava)
                myHome\t~
                bin\t~/test/bin	# My comment
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarkFile());
        assertEquals(2, ctx.bookmarkStream().count());

    }

    @Test
    void fixTest() throws Exception {
        var random = new Random();
        var ctx = DirBookContext.of();
        var instance = ctx.instance;

        instance.mainRun(list("save", homeDir, "home"));
        assertEquals(1, ctx.bookmarkStream().count());


        instance.mainRun(list("save", "/test/%s.tmp".formatted(random.nextLong()) , "temp"));
        assertEquals(2, ctx.bookmarkStream().count());


        instance.mainRun(list("fix"));
        var removed = ctx.getOut();
        assertTrue(removed.startsWith("Removed: temp"));
        assertEquals(1, ctx.bookmarkStream().count());
    }

    @Test
    void bookmarksTest() throws Exception {
        var ctx = DirBookContext.of();
        var instance = ctx.instance;
        var myDir = "/temp/bin/local";

        instance.mainRun(list("save", myDir, "a"));
        instance.mainRun(list("save", myDir, "b"));
        instance.mainRun(list("save", myDir, "c"));
        instance.mainRun(list("save", homeDir, "home"));

        assertEquals(4, ctx.bookmarkStream().count());
        instance.mainRun(list("bookmarks", myDir));
        var output = ctx.getOutLines().collect(Collectors.joining(","));
        assertEquals("a,b,c", output);
    }

    @Test
    void getSubdirTest() throws Exception {
        var ctx = DirBookContext.of();
        var instance = ctx.instance;
        var myDir = "/temp/bin";
        instance.mainRun(list("save", myDir, "bin"));
        assertEquals(1, ctx.bookmarkStream().count());

        instance.mainRun(list("list", "bin/subdirectory/abc"));
        var subdir = ctx.getOut();
        var expected = "/temp/bin/subdirectory/abc\n";
        assertEquals(expected, subdir);

        instance.mainRun(list("list", "bin\\subdirectory\\abc"));
        subdir = ctx.getOut();
        expected = "/temp/bin\\subdirectory\\abc\n";
        assertEquals(expected, subdir);
    }

    @Test
    void getSubdirWithCommentTest() throws Exception {
        var ctx = DirBookContext.of();
        var instance = ctx.instance;
        var myDir = "/temp/bin";
        instance.mainRun(list("save", myDir, "bin", "Comment"));
        assertEquals(1, ctx.bookmarkStream().count());

        instance.mainRun(list("list", "bin/subdirectory/abc"));
        var subdir = ctx.getOut();
        var expected = "/temp/bin/subdirectory/abc\n";
        assertEquals(expected, subdir);

        instance.mainRun(list("list", "bin/subdirectory/abc"));
        subdir = ctx.getOut();
        expected = "/temp/bin/subdirectory/abc\n";
        assertEquals(expected, subdir);
    }

    @Test
    void regexpTest() throws Exception {
        // Input values
        var key = "a";
        var value = "xxx";
        var cellSeparator = "x#";
        var text1 = "axxxx#cc";
        var text2 = "axxx";

        var pattern = Pattern.compile("%s((?:(?!%s).)*)".formatted(
                Pattern.quote(key),
                Pattern.quote(cellSeparator)));

        // Check test1:
        var matcher1 = pattern.matcher(text1);
        assertTrue(matcher1.find(), "Pattern should match input");
        var result1 = matcher1.group(1); // capture group 1
        assertEquals(value, result1, "Captured group should contain expected text");

        // Check test2:
        var matcher2 = pattern.matcher(text2);
        assertTrue(matcher2.find(), "Pattern should match input");
        var result2 = matcher2.group(1); // capture group 1
        assertEquals(value, result2, "Captured group should contain expected text");
    }

    // =========== UTILS ===========

    private DirectoryBookmarks.List<String> list(String... args) {
        return DirectoryBookmarks.List.of(args);
    }

    record DirBookContext  (
            DirectoryBookmarks instance,
            File storeName,
            ByteArrayOutputStream out,
            PrintStream printOut,
            ByteArrayOutputStream err,
            PrintStream printErr
    ) {
        public String getOut() {
            printOut.flush();
            var result = out.toString(DirectoryBookmarksTest.charset);
            out.reset();
            return result;
        }

        public String getErr() {
            printErr.flush();
            var result = err.toString(DirectoryBookmarksTest.charset);
            err.reset();
            return result;
        }

        public String bookmarkFile() throws IOException {
            return Files.readString(storeName.toPath());
        }

        public Stream<String> bookmarkStream() throws Exception {
            out.reset();
            instance.mainRun(DirectoryBookmarks.List.of("list"));
            return getOutLines();
        }

        public String bookmarksString() throws Exception {
            return bookmarkStream().collect(Collectors.joining("\n"));
        }

        @Override
        public String toString() {
            return out.toString(DirectoryBookmarksTest.charset);
        }

        public static DirBookContext of() {
            var file = createFile();
            var outStream = new ByteArrayOutputStream();
            var outPrint = new PrintStream(outStream);
            var errStream = new ByteArrayOutputStream();
            var errPrint = new PrintStream(outStream);
            var bookmarks = new DirectoryBookmarks(file, outPrint, errPrint, true, true);
            return new DirBookContext(bookmarks, file, outStream, outPrint, errStream, errPrint);
        }

        public Stream<String> getOutLines() {
            return Stream.of(getOut().trim().split("\n"));
        }

        private static File createFile() {
            try {
                var result = File.createTempFile("directoryBookmark", ".temp");
                result.deleteOnExit();
                return result;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}