/*
 * Author: Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 * Licence: Apache License, Version 2.0
 */

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class ArchiveTest {
    @Test
    void testBase64InputStream1() throws IOException {
        var items = new String[]{"abc", "de"};
        var os = new ByteArrayOutputStream();
        try (var is = new Archive.Base64InputStream(items)) {
            var buffer = new byte[2];
            var length = 0;
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }
        }
        Assertions.assertEquals("abcde", os.toString(StandardCharsets.UTF_8));

        os.reset();
        try (var is = new Archive.Base64InputStream(items)) {
            is.transferTo(os);
        }
        Assertions.assertEquals("abcde", os.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testBase64InputStream2() throws IOException {
        var items = new String[]{"abc", "de"};
        var os = new ByteArrayOutputStream();
        try (var is = new Archive.Base64InputStream(items)) {
            var buffer = new byte[20];
            var length = 0;
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }
        }
        Assertions.assertEquals("abcde", os.toString(StandardCharsets.UTF_8));
    }
}