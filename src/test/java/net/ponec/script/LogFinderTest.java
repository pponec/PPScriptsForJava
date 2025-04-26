package net.ponec.script;

import net.ponec.script.utils.MemoryPrintStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogFinderTest {

    @Test
    public void run_one() throws IOException {

        var regexp = "ERROR";
        var body = """
                Row one
                Row two
                Row three
                ERROR is here
                Row five
                Row six
                Row seven
                """;

        var testFile = Files.createTempFile("test_", ".txt");
        var out = new MemoryPrintStream();
        Files.writeString(testFile, body);

        var lineCount = 2;
        new LogFinder(out, lineCount, lineCount).run(list(regexp, testFile.toString()));
        var expected = """
                ### /tmp/test.txt:2 #1
                Row two
                Row three
                (/tmp/test.txt:4) ERROR is here
                Row five
                Row six
                """;
        var result = removeNumbers(out.toString());
        assertEquals(expected, result);

        Files.deleteIfExists(testFile);
    }


    @Test
    public void run_more() throws IOException {
        var regexp = "ERROR";
        var body = """
                Row one
                ERROR A is here
                Row three
                Row four
                Row five
                ERROR B is here
                Row seven
                Row eight
                """;

        var testFile = Files.createTempFile("test_", ".txt");
        var out = new MemoryPrintStream();
        Files.writeString(testFile, body);

        var lineCount = 1;
        new LogFinder(out, lineCount, lineCount).run(list(regexp, testFile.toString()));
        var expected = """
                ### /tmp/test.txt:1 #1
                Row one
                (/tmp/test.txt:2) ERROR A is here
                Row three
                
                ### /tmp/test.txt:5 #2
                Row five
                (/tmp/test.txt:6) ERROR B is here
                Row seven
                """;
        var result = removeNumbers(out.toString());
        assertEquals(expected, result);

        Files.deleteIfExists(testFile);
    }

    @Test
    public void run_long() throws IOException {
        var regexp = "ERROR";
        var body = """
                Row one
                ERROR A is here
                Row three
                Row four
                Row five
                ERROR B is here
                Row seven
                Row eight
                """;

        var testFile = Files.createTempFile("test_", ".txt");
        var out = new MemoryPrintStream();
        Files.writeString(testFile, body);

        new LogFinder(out, 9, 99).run(list(regexp, testFile.toString()));
        var expected = """
                ### /tmp/test.txt:1 #1
                Row one
                (/tmp/test.txt:2) ERROR A is here
                Row three
                Row four
                Row five
                
                ### /tmp/test.txt:6 #2
                (/tmp/test.txt:6) ERROR B is here
                Row seven
                Row eight
                """;
        var result = removeNumbers(out.toString());
        assertEquals(expected, result);

        Files.deleteIfExists(testFile);
    }

    @Test
    public void run_zip() throws IOException {
        var regexp = "ERROR";
        var testFile = createZipFile();
        var out = new MemoryPrintStream();
        var lineCount = 1;
        new LogFinder(out, lineCount, lineCount).run(list(regexp, testFile.toString()));
        var expected = """
                ### file1.txt:1 #1
                Row one
                (file1.txt:2) ERROR A is here
                Row three
                
                ### file1.txt:5 #2
                Row five
                (file1.txt:6) ERROR B is here
                Row seven
                ### file2.txt:1 #1
                Row one
                (file2.txt:2) ERROR A is here
                Row three
                
                ### file2.txt:5 #2
                Row five
                (file2.txt:6) ERROR B is here
                Row seven
                """;
        var result = removeNumbers(out.toString());
        assertEquals(expected, result);

        Files.deleteIfExists(testFile);
    }


    private LogFinder.List list(String... items) {
        return (LogFinder.List.of(items));
    }

    private String removeNumbers(String text) {
        return text.replaceAll("_(\\d+)\\.", ".");
    }

    public static Path createZipFile() throws IOException {
        var body = """
                Row one
                ERROR A is here
                Row three
                Row four
                Row five
                ERROR B is here
                Row seven
                Row eight
                """;

        var tempZip = Files.createTempFile("test-", ".zip");
        tempZip.toFile().deleteOnExit();

        try (var fos = Files.newOutputStream(tempZip);
             var zipOut = new ZipOutputStream(fos)) {

            // Přidání prvního souboru
            addEntry(zipOut, "file1.txt", body);

            // Přidání druhého souboru
            addEntry(zipOut, "file2.txt", body);
        }

        return tempZip;
    }

    private static void addEntry(
            ZipOutputStream zipOut,
            String filename,
            String content
    ) throws IOException {
        var entry = new ZipEntry(filename);
        zipOut.putNextEntry(entry);
        zipOut.write(content.getBytes());
        zipOut.closeEntry();
    }
}