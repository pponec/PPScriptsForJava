package net.ponec.script.utils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Custom PrintStream that stores all printed content in memory
 */
public class MemoryPrintStream extends PrintStream {

    public MemoryPrintStream() {
        super(new ByteArrayOutputStream());
    }

    @Override
    public String toString() {
        return out.toString();
    }

    public boolean isEmpty() {
        return ((ByteArrayOutputStream) out).size() == 0;
    }
}