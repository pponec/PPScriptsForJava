package net.ponec.script;

import org.junit.jupiter.api.Test;

import net.ponec.script.PPUtils.List;

import static org.junit.jupiter.api.Assertions.*;

class ListTest {

    private final List<Character> list = createList();
    private final List<Character> empty = List.of();

    private final Character undef = 'X';

    @Test
    void testClone() {
        List<Character> clone = list.clone();
        assertNotSame(list, clone);
        assertEquals(list.get(0, 'A'), clone.get(0, 'B'));
        assertEquals(list.get(4, 'A'), clone.get(4, 'B'));
        assertArrayEquals(list.stream().toArray(), clone.stream().toArray());
    }

    @Test
    void getItem() {
        assertEquals('A', list.get(0, undef));
        assertEquals('B', list.get(1, undef));
        assertEquals('E', list.get(4, undef));
        assertEquals('X', list.get(5, undef));
        assertEquals('E', list.get(-1, undef));
        assertEquals('D', list.get(-2, undef));
        assertEquals(undef, empty.get(0, undef));
        assertEquals(undef, list.get(9, undef));
    }

    @Test
    void getFirst() {
        assertEquals('A', list.getFirst(undef));
        assertEquals(undef, empty.getFirst(undef));
    }

    @Test
    void getLast() {
        assertEquals('E', list.getLast(undef));
        assertEquals(undef, empty.getLast(undef));
    }


    @Test
    void subList() {
        List<Character> trim = list.clone().subList(3);
        assertEquals(2, trim.size());
        assertEquals('D', trim.get(0, 'C'));
    }

    @Test
    void add() {
        List<Character> extended = list.clone();
        extended.addAll(List.of('P', 'C'));
        assertEquals(list.size() + 2, extended.size());
        assertEquals('P', extended.get(5, 'C'));
        assertEquals('C', extended.get(6, 'C'));
    }

    @Test
    void toClone() {
        List<Character> list = this.list.clone();

        assertEquals(this.list.get(0, 'X'), list.get(0));
        assertEquals(this.list.get(1, 'X'), list.get(1));
        assertEquals(this.list.get(4, 'X'), list.get(4));
        assertEquals(this.list.size(), list.size());
    }

    @Test
    void isEmpty() {
        assertFalse(list.isEmpty());
        assertTrue(empty.isEmpty());
    }

    @Test
    void size() {
        assertEquals(5, list.size());
        assertEquals(0, empty.size());
    }

    @Test
    void stream() {
        List<Character> list = List.of(this.list);

        assertEquals(this.list.get(0, 'X'), list.get(0, 'y'));
        assertEquals(this.list.get(1, 'X'), list.get(1, 'y'));
        assertEquals(this.list.get(4, 'X'), list.get(4, 'y'));
        assertEquals(this.list.size(), list.size());
    }

    @Test
    void testHashCode() {
        List<Character> other = createList();
        assertEquals(list.hashCode(), other.hashCode());
        assertNotEquals(list.hashCode(), empty.hashCode());
    }

    @Test
    void testEquals() {
        List<Character> other = createList();
        assertEquals(list, other);
        assertNotEquals(list, empty);
    }

    // - - - - - - - - - - -

    List<Character> createList() {
        return List.of('A', 'B', 'C', 'D', 'E');
    }
}