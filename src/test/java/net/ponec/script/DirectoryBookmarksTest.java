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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class DirectoryBookmarksTest {

    private static final String homeDir = DirectoryBookmarks.USER_HOME.toString();
    private static final Charset charset = StandardCharsets.UTF_8;

    @Test
    void mainRunTest() throws Exception {
        var ctx = DirBookContext.of();
        var instance = ctx.instance;
        instance.mainRun(array("save", "/test/dev", "bin", "My", "comment"));
        var bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/PPScriptsForJava)
                bin\t/test/dev	# My comment
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarkFile());
        assertEquals("", ctx.getOut());

        instance.mainRun(array("save", "/test/conf", "conf"));
        bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/PPScriptsForJava)
                conf\t/test/conf
                bin\t/test/dev	# My comment
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarkFile());
        assertEquals("", ctx.getOut());

        instance.mainRun(array("save", "/test/bin", "bin"));
        bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/PPScriptsForJava)
                bin\t/test/bin
                conf\t/test/conf
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarkFile());
        assertEquals("", ctx.getOut());
        assertEquals("", ctx.getErr());

        instance.mainRun(array("list", "bin"));
        assertEquals("/test/bin\n", ctx.getOut());
        assertEquals("", ctx.getErr());

        bookmarks = """
                bin\t/test/bin
                conf\t/test/conf
                """.trim();
        assertEquals(bookmarks, ctx.bookmarksString());
        assertEquals("", ctx.getErr());

        instance.mainRun(array("version"));
        assertEquals(instance.appVersion + "\n", ctx.getOut());

        instance.mainRun(array("delete", "conf"));
        bookmarks = "bin\t/test/bin";
        assertEquals(bookmarks, ctx.bookmarksString());
        assertEquals("", ctx.getErr());

        var ex = assertThrows(RuntimeException.class, () ->
                instance.mainRun(array("list", "conf")));
        assertEquals("Bookmark [conf] has no directory.", ex.getMessage());

        instance.mainRun(array("list", "~"));
        assertEquals(homeDir + "\n", ctx.getOut());
        assertEquals("", ctx.getErr());

        bookmarks = "bin\t/test/bin";
        assertEquals(bookmarks, ctx.bookmarksString());
    }

    @Test
    void getHomeTest() throws Exception {
        var ctx = DirBookContext.of();
        var instance = ctx.instance;
        instance.mainRun(array("save", homeDir + "/test/bin", "bin", "My", "comment"));

        var bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/PPScriptsForJava)
                bin\t~/test/bin	# My comment
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarkFile());
        assertEquals("", ctx.getOut());

        instance.mainRun(array("get"));
        assertEquals(homeDir + "\n", ctx.getOut());

        instance.mainRun(array("save", "~", "myHome"));
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

        instance.mainRun(array("save", homeDir, "home"));
        assertEquals(1, ctx.bookmarkStream().count());


        instance.mainRun(array("save", "/test/%s.tmp".formatted(random.nextLong()) , "temp"));
        assertEquals(2, ctx.bookmarkStream().count());


        instance.mainRun(array("fix"));
        var removed = ctx.getOut();
        assertTrue(removed.startsWith("Removed: temp"));
        assertEquals(1, ctx.bookmarkStream().count());
    }

    @Test
    void bookmarksTest() throws Exception {
        var ctx = DirBookContext.of();
        var instance = ctx.instance;
        var myDir = "/temp/bin/local";

        instance.mainRun(array("save", myDir, "a"));
        instance.mainRun(array("save", myDir, "b"));
        instance.mainRun(array("save", myDir, "c"));
        instance.mainRun(array("save", homeDir, "home"));

        assertEquals(4, ctx.bookmarkStream().count());
        instance.mainRun(array("bookmarks", myDir));
        var output = ctx.getOutLines().collect(Collectors.joining(","));
        assertEquals("a,b,c", output);
    }


    @Test
    void getSubdirTest() throws Exception {
        var ctx = DirBookContext.of();
        var instance = ctx.instance;
        var myDir = "/temp/bin";
        instance.mainRun(array("save", myDir, "bin"));
        assertEquals(1, ctx.bookmarkStream().count());

        instance.mainRun(array("list", "bin/subdirectory/abc"));
        var subdir = ctx.getOut();
        var expected = "/temp/bin/subdirectory/abc\n";
        assertEquals(expected, subdir);

        instance.mainRun(array("list", "bin\\subdirectory\\abc"));
        subdir = ctx.getOut();
        expected = "/temp/bin\\subdirectory\\abc\n";
        assertEquals(expected, subdir);
    }

    // =========== UTILS ===========

    private DirectoryBookmarks.Array<String> array(String... args) {
        return DirectoryBookmarks.Array.of(args);
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
            instance.mainRun(DirectoryBookmarks.Array.of("list"));
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