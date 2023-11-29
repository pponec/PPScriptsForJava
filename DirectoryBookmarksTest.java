// The test must be compiled to the run.
// Licence: Apache License, Version 2.0, https://github.com/pponec/

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class DirectoryBookmarksTest {

    private final Charset charset = StandardCharsets.UTF_8;

    public static void main(String[] args) throws Exception {
        new DirectoryBookmarksTest();
    }

    public DirectoryBookmarksTest() throws IOException {
        var file = createFile();

        var outputStream = new ByteArrayOutputStream();
        try (var printStream = new PrintStream(outputStream, true, charset)) {
            var instance = new DirectoryBookmarks(file, printStream, System.err, false, false);
            sdfTest(instance, outputStream);
        }

        outputStream = new ByteArrayOutputStream();
        try (var printStream = new PrintStream(outputStream, true, charset)) {
            var instance = new DirectoryBookmarks(file, printStream, System.err, false, false);
            ldfTest(instance, outputStream);
        }
    }

    void sdfTest(DirectoryBookmarks instance, ByteArrayOutputStream os) {
       // TODO
    }

    void ldfTest(DirectoryBookmarks instance, ByteArrayOutputStream os) {
       // TODO
    }

    private File createFile() throws IOException {
        var result = File.createTempFile("directoryBookmark", "temp");
        result.deleteOnExit();
        return result;
    }

}