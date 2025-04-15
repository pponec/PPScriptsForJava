package net.ponec.script;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PPUtilsTest {
    private final String undef = "?";

    @Test
    void jsonTest() {
        var json = """
                { "a": "A"
                , "b":  1
                , "c": 2.2
                , "d": true
                , "e": null
                , "f": { "g": "G", "h": 2 }
                , "z": ["x", "y"]
                }
                """;

        var map = PPUtils.Json.of(json);
        assertEquals(map.get("a").get(), "A");
        assertEquals(map.get("b").get(), 1L);
        assertEquals(map.get("c").get(), 2.2);
        assertEquals(map.get("d").get(), true);
        assertEquals(map.get("e").orElse(null), null);
        assertEquals(map.get("f.g").get(), "G");
        assertEquals(map.get("f.h").get(), 2L);
        assertEquals(map.get("f").get().toString(), "{g=G, h=2}");

        assertEquals(map.get("x").orElse(undef), undef);
        assertEquals(map.get("x.y").orElse(undef), undef);
        assertEquals(map.get("a.z").orElse(undef), undef);
        assertEquals(map.get("z").orElse(undef), undef);
    }

    @Test
    void jsonWithMultilineCommentsTest() {
        var json = """ 
                /* Test JSON */
                { "a": "A"
                /* Integer value
                , "b":  1  */
                /* Decimal value */
                , "c": 2.2
                /* Boolean value */
                , "d": true
                , "e": null
                /* SubObject */
                , "f": { "g": "G", "h": 2 }
                , "z": ["x", "y"]
                }
                """;

        var map = PPUtils.Json.of(json);
        assertEquals(map.get("a").get(), "A");
        assertEquals(map.get("b").get(), 1L); // WARNING
        assertEquals(map.get("c").get(), 2.2);
        assertEquals(map.get("d").get(), true);
        assertEquals(map.get("e").orElse(null), null);
        assertEquals(map.get("f.g").get(), "G");
        assertEquals(map.get("f.h").get(), 2L);
        assertEquals(map.get("f").get().toString(), "{g=G, h=2}");

        assertEquals(map.get("x").orElse(undef), undef);
        assertEquals(map.get("x.y").orElse(undef), undef);
        assertEquals(map.get("a.z").orElse(undef), undef);
        assertEquals(map.get("z").orElse(undef), undef);
    }




    @Test
    void jsonWithSingleLineCommentsTest() {
        var json = """ 
                // Test JSON
                { "a": "A"
                // Integer value
                , "b":  1
                /* Decimal value
                , "c": 2.2
                // Boolean value
                , "d": true
                , "e": null
                // SubObject
                , "f": { "g": "G", "h": 2 }
                , "z": ["x", "y"]
                }
                """;

        var map = PPUtils.Json.of(json);
        assertEquals(map.get("a").get(), "A");
        assertEquals(map.get("b").get(), 1L);
        assertEquals(map.get("c").get(), 2.2);
        assertEquals(map.get("d").get(), true);
        assertEquals(map.get("e").orElse(null), null);
        assertEquals(map.get("f.g").get(), "G");
        assertEquals(map.get("f.h").get(), 2L);
        assertEquals(map.get("f").get().toString(), "{g=G, h=2}");

        assertEquals(map.get("x").orElse(undef), undef);
        assertEquals(map.get("x.y").orElse(undef), undef);
        assertEquals(map.get("a.z").orElse(undef), undef);
        assertEquals(map.get("z").orElse(undef), undef);
    }

    @Test
    void archive() throws IOException {
        var compressor = new PPUtils.ScriptArchiveBuilder(false);
        var archive = Files.createTempFile("Archiv", ".java");
        var file1 =  Files.createTempFile("Test1", ".txt");
        var file2 =  Files.createTempFile("Test2", ".txt");

        try {
            Files.writeString(file1, "Hallo World");
            Files.writeString(file2, IntStream.range(0, 1000).mapToObj(i -> ".").collect(Collectors.joining()));
            compressor.build(archive, Set.of(file1, file2));
            Assertions.assertTrue(Files.isReadable(archive));

            var javaClass = Files.readString(archive);
            Assertions.assertTrue(javaClass.contains("/" + file1.getFileName()));
            Assertions.assertTrue(javaClass.contains("/" + file2.getFileName()));
            Assertions.assertTrue(javaClass.contains("eJzzSMzJyVcIzy/KSQEAF+MEGQ=="));
            Assertions.assertTrue(javaClass.contains("eJzT0xsFo2AUDHcAAGYRs7E="));
        } finally {
            Stream.of(archive, file1, file2).forEach(f -> deleteFile(f));
        }
    }

    @Test
    void grepf_withFile() throws Exception {
        var statement = "grepf";
        var line = "a-hello-world-c";
        var pattern = "a-(.*)-(.*)-c";
        var formatter = "${file}:: a:%s, b:%s";
        var expected = "temp:: a:hello, b:world";
        var file = Files.createTempFile("test", ".temp");
        var charset = StandardCharsets.UTF_8;
        var params = PPUtils.List.of(statement, pattern, formatter, file.toAbsolutePath().toString());

        var out = new ByteArrayOutputStream();
        var printer = new PrintStream(out, true, charset);
        var ppUtils = new PPUtils(printer);

        Files.writeString(file, line, charset);
        ppUtils.mainRun(params);
        Files.delete(file);

        String result = out.toString(charset).trim();
        result = result.substring(result.length() - expected.length());
        assertEquals(expected, result);
    }

    @Test
    void grepf_noFile() throws Exception {
        var statement = "grepf";
        var line = "a-hello-world-c";
        var pattern = "a-(.*)-(.*)-c";
        var formatter = "a:%s, b:%s";
        var expected = "a:hello, b:world";
        var file = Files.createTempFile("test", ".temp");
        var charset = StandardCharsets.UTF_8;
        var params = PPUtils.List.of(statement, pattern, formatter, file.toAbsolutePath().toString());

        var out = new ByteArrayOutputStream();
        var printer = new PrintStream(out, true, charset);
        var ppUtils = new PPUtils(printer);

        Files.writeString(file, line, charset);
        ppUtils.mainRun(params);
        Files.delete(file);

        var result = out.toString().trim();
        assertEquals(expected, result);
    }

    private static void deleteFile(Path f) {
        try {
            Files.deleteIfExists(f);
        } catch (IOException e) {
            Logger.getLogger(PPUtilsTest.class.getName()).severe("Can't delete file: " + f);
        }
    }
}