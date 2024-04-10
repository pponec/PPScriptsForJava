/*
 * Author: Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 * Licence: Apache License, Version 2.0
 */

package net.ponec.script;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
/** A template for the Script Archive for Java 17
 * @see PPUtils.ScriptArchiveBuilder#build(Path, List)
 * @version 2024-04-10T20:00 */
public final class ScriptArchiveTemplate {
    public static void main(String[] args) throws IOException {
        java.util.stream.Stream.of(null
            , new File("temp/test.txt", "eJwDAAAAAAE=")
        ).skip(1).forEach(file -> write(file));
    }
    public static void write(File file) {
        try {
            var path = Path.of(file.path);
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            var base64is = new ByteArrayInputStream(file.base64Body.getBytes(StandardCharsets.US_ASCII));
            var is = new InflaterInputStream(Base64.getDecoder().wrap(base64is), new Inflater());
            try (var os = Files.newOutputStream(path)) {
                var buffer = new byte[1024];
                var length = 0;
                while ((length = is.read(buffer)) != -1) { os.write(buffer, 0, length); }
            }
            System.out.println("Restored: " + path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to extract file: " + file.path, e);
        }
    }
    record File(String path, String base64Body) {}
}