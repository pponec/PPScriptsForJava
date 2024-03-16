package utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PPUtilsTest {

    @Test
    void jsonTest() {
        var json = """
                { "a": "A"
                , "b":  1
                , "c": 2.2
                , "d": true
                , "e": null
                , "f": { "g": "G", "h": 2 }
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

        assertEquals(map.get("x").orElse("X"), "X");
        assertEquals(map.get("x.y").orElse("Y"), "Y");
        assertEquals(map.get("a.z").orElse("Z"), "Z");
    }

}