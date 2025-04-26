package net.ponec.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircularBufferTest {

    @Test
    void processTextReader_short() {

        var instance = new LogFinder.CircularBuffer(10);
        for (int i = 1; i <= 3; i++) {
            instance.add("r" + i);
        }
        var expected = """
                r1
                r2
                r3
                """.trim();
        assertEquals(expected, instance.toString());
    }

    @Test
    void processTextReader_full() {

        var instance = new LogFinder.CircularBuffer(3);
        for (int i = 1; i <= 9; i++) {
            instance.add("r" + i);
        }
        var expected = """
                r7
                r8
                r9
                """.trim();
        assertEquals(expected, instance.toString());
    }
}