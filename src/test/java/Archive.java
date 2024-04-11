/*
 * Author: Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 * Licence: Apache License, Version 2.0
 */

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;
/** A template for the Script Archive for Java 17
 * @see net.ponec.script.PPUtils.ScriptArchiveBuilder#build(Path, List)
 * @version 2024-04-10T20:00 */
public final class Archive {
    public static void main(String[] args) throws IOException {
        java.util.stream.Stream.of(null
                , new File("temp/test.txt", "eJzzSM", "zJyVcIzy/", "KSQEAF+MEGQ==")
        ).skip(1).forEach(file -> write(file));
    }
    public static void write(File file) {
        try {
            var path = Path.of(file.path);
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            var base64is = new Base64InputStream(file.base64Body);
            var is = new InflaterInputStream(Base64.getDecoder().wrap(base64is), new Inflater());
            try (var os = new PrintStream(Files.newOutputStream(path))) { is.transferTo(os); }
            System.out.println("Restored: " + path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to extract file: " + file.path, e);
        }
    }
    record File(String path, String... base64Body) {}
    public static final class Base64InputStream extends InputStream {
        private final StringReader[] readers;
        private final byte[] oneByte = new byte[1];
        private char[] buffer = new char[0];
        private int idx;

        public Base64InputStream(String... body) {
            this.readers = Arrays.stream(body).map(r -> new StringReader(r)).toArray(StringReader[]::new);
        }
        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            if (buffer.length < len) { buffer = new char[len]; }
            final var result = readers[idx].read(buffer, off, len);
            for (int i = 0; i < result; i++) { b[i] = (byte) buffer[i]; }
            return (result >= 0 || ++idx == readers.length) ? result : read(b, off, len);
        }
        @Override
        public int read() throws IOException {
            final var result = read(oneByte, 0, 1);
            return result < 0 ? result : oneByte[0];
        }
        @Override
        public void close() {
            Stream.of(readers).forEach(r -> r.close());
        }
        @Override
        public long skip(long n) throws IOException {
            throw new UnsupportedEncodingException();
        }
    }
}