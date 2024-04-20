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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DirectoryBookmarksTest {

    private static final Charset charset = StandardCharsets.UTF_8;

    @Test
    void mainRunTest() throws Exception {
        var ctx = DirBookContext.of();
        var instance = ctx.instance;
        instance.mainRun(array("save", "/test/dev", "bin", "My", "comment"));
        var bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/DirectoryBookmarks)
                bin	/test/dev	# My comment
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarks());
        assertEquals("", ctx.getOut());

        instance.mainRun(array("save", "/test/conf", "conf"));
        bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/DirectoryBookmarks)
                conf	/test/conf
                bin	/test/dev	# My comment
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarks());
        assertEquals("", ctx.getOut());

        instance.mainRun(array("save", "/test/bin", "bin"));
        bookmarks = """
                # DirectoryBookmarks %s (https://github.com/pponec/DirectoryBookmarks)
                bin	/test/bin
                conf	/test/conf
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.bookmarks());
        assertEquals("", ctx.getOut());
        assertEquals("", ctx.getErr());

        instance.mainRun(array("list", "bin"));
        assertEquals("/test/bin\n", ctx.getOut());
        assertEquals("", ctx.getErr());

        var ex = assertThrows(RuntimeException.class, () ->
                instance.mainRun(array("list", "xxx")));
        assertEquals("Bookmark [xxx] has no directory.", ex.getMessage());

        instance.mainRun(array("list"));
        bookmarks = """
                bin	/test/bin
                conf	/test/conf
                """.formatted(instance.appVersion);
        assertEquals(bookmarks, ctx.getOut());
        assertEquals("", ctx.getErr());

        instance.mainRun(array("version"));
        assertEquals(instance.appVersion + "\n", ctx.getOut());

        // DELETE ONE BOOKMARK:
        instance.mainRun(array("save", "", "conf"));
        instance.mainRun(array("list"));
        bookmarks = "bin	/test/bin\n";
        assertEquals(bookmarks, ctx.getOut());
        assertEquals("", ctx.getErr());
    }

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

        public String bookmarks() throws IOException {
            return Files.readString(storeName.toPath());
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