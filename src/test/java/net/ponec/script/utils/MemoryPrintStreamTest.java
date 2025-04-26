package net.ponec.script.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Custom PrintStream that stores all printed content in memory
 */
public class MemoryPrintStreamTest {

    @Test
    public void MemoryPrintStreamTest() {
        var out = new MemoryPrintStream();
        assertTrue(out.isEmpty());
        out.println("test 1");
        out.printf("test %s%n", 2);
        out.println("test 3");

        var expected = """
                test 1
                test 2
                test 3
                """;

        assertEquals(expected, out.toString());
        assertFalse(out.isEmpty());
    }
}