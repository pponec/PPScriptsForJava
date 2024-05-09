package net.ponec.script;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import net.ponec.script.PPUtils.Array;

import static org.junit.jupiter.api.Assertions.*;

class ArrayTest {

    private final Array<Character> array = createArray();
    private final Array<Character> empty = Array.of();

    private final Character undef = 'X';

    @Test
    void testClone() {
        Array<Character> clone = array.clone();
        assertNotSame(array, clone);
        assertEquals(array.getItem(0), clone.getItem(0));
        assertEquals(array.getItem(4), clone.getItem(4));
        assertArrayEquals(array.stream().toArray(), clone.stream().toArray());
    }

    @Test
    void getItem() {
        assertEquals('A', array.get(0).orElse(undef));
        assertEquals('B', array.get(1).orElse(undef));
        assertEquals('E', array.get(4).orElse(undef));
        assertEquals('X', array.get(5).orElse(undef));
        assertEquals('E', array.get(-1).orElse(undef));
        assertEquals('D', array.get(-2).orElse(undef));
        assertEquals(undef, empty.get(0).orElse(undef));
        assertEquals(undef, array.get(9).orElse(undef));
    }

    @Test
    void getFirst() {
        assertEquals('A', array.getFirst().orElse(undef));
        assertEquals(undef, empty.getFirst().orElse(undef));
    }

    @Test
    void getLast() {
        assertEquals('E', array.getLast().orElse(undef));
        assertEquals(undef, empty.getLast().orElse(undef));
    }

    @Test
    void removeFirst() {
        Array<Character> trim = array.removeFirst();
        assertEquals(5, array.size());
        assertEquals(4, trim.size());
        assertEquals('B', trim.getItem(0));

        trim = empty.removeFirst();
        assertEquals(0, trim.size());
    }

    @Test
    void subArray() {
        Array<Character> trim = array.subArray(3);
        assertEquals(2, trim.size());
        assertEquals('D', trim.getItem(0));
    }

    @Test
    void add() {
        Array<Character> extended = array.add('P', 'C');
        assertEquals(array.size() + 2, extended.size());
        assertEquals('P', extended.getItem(5));
        assertEquals('C', extended.getItem(6));
    }

    @Test
    void toList() {
        List<Character> list = array.toList();

        assertEquals(array.getItem(0), list.get(0));
        assertEquals(array.getItem(1), list.get(1));
        assertEquals(array.getItem(4), list.get(4));
        assertEquals(array.size(), list.size());
    }

    @Test
    void isEmpty() {
        assertFalse(array.isEmpty());
        assertTrue(empty.isEmpty());
    }

    @Test
    void size() {
        assertEquals(5, array.size());
        assertEquals(0, empty.size());
    }

    @Test
    void stream() {
        List<Character> list = array.stream().toList();

        assertEquals(array.getItem(0), list.get(0));
        assertEquals(array.getItem(1), list.get(1));
        assertEquals(array.getItem(4), list.get(4));
        assertEquals(array.size(), list.size());
    }

    @Test
    void testHashCode() {
        Array<Character> other = createArray();
        assertEquals(array.hashCode(), other.hashCode());
        assertNotEquals(array.hashCode(), empty.hashCode());
    }

    @Test
    void testEquals() {
        Array<Character> other = createArray();
        assertEquals(array, other);
        assertNotEquals(array, empty);
    }

    // - - - - - - - - - - -

    Array<Character> createArray() {
        return Array.of('A', 'B', 'C', 'D', 'E');
    }
}