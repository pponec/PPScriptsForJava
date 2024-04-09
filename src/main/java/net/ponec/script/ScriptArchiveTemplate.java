/*
 * Author: Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 * Licence: Apache License, Version 2.0
 */

package net.ponec.script;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
/** @version 2024-04-10T20:00 */
public final class ScriptArchiveTemplate {
    public static void main(String[] args) throws IOException {
        Arrays.asList(
                new File("temp/test.txt", "dGVzdA==")
        ).stream().forEach(file -> {
            try {
                var path = Path.of(file.path);
                if (path.getParent() != null) Files.createDirectories(path.getParent());
                Files.write(path, decompress(Base64.getDecoder().decode(file.base64Body)));
                System.out.println("Restored: " + path);
            } catch (IOException e) { throw new RuntimeException("Extracting the file %s fails".formatted(file.path), e); }
        });
    }
    record File(String path, String base64Body) {};

    public static byte[] decompress(byte[] data) throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var bais = new ByteArrayInputStream(data);
             var iis = new InflaterInputStream(bais, new Inflater())) {
            var buffer = new byte[1024];
            var length = 0;
            while ((length = iis.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }
        }
        return baos.toByteArray();
    }
}