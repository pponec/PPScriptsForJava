package net.ponec.script;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Unit test class for {@link TreeModel}.
 * <p>
 * This class verifies correctness of the TreeModel class methods
 * for parsing and exporting data between PROPERTIES and YAML formats.
 * All values are expected to be simple strings.
 * <p>
 * The tests cover:
 * <ul>
 *     <li>Conversion from PROPERTIES to YAML</li>
 *     <li>Conversion from YAML to PROPERTIES</li>
 *     <li>Retrieval of values using dot-notation keys</li>
 * </ul>
 */
public class TreeModelTest {

    @Test
    public void ofProps() {
        var props = """
            user.name=TEST
            user.name=John
            user.name=Růžena
            user.age= 30
            user.address.city = London
            user.note =
            #Comment= 
            """;

        var treeModel = TreeModel.ofProps(props);
        var yaml = treeModel.toYaml();

        assertTrue(yaml.contains("name: Růžena"));
        assertTrue(yaml.contains("age: 30"));
        assertTrue(yaml.contains("city: London"));
        assertTrue(yaml.contains("note:"));
        assertFalse(yaml.contains("Users"));

        assertEquals("Růžena", treeModel.getValue("user.name"));
        assertEquals("30", treeModel.getValue("user.age"));
        assertEquals("London", treeModel.getValue("user.address.city"));
        assertEquals("", treeModel.getValue("user.note"));
    }

    @Test
    public void ofYamlPlain() {
        var yaml = """
            user:
              name: Julien
              contact: 
                email: test@test.test
                phone: 123456789
                address:
                  city: Paris
                  street: Trocadéro
              age  : 99
              login:
              note : 'A long text'
              noteq: ''''
              url  : 'https://test.txt'
            #Comment:
            """;

        var treeModel = TreeModel.ofYaml(yaml);
        assertEquals("Paris", treeModel.getValue("user.contact.address.city"));
        assertEquals("99", treeModel.getValue("user.age"));
        assertEquals(null, treeModel.getValue("user.login"));
        assertEquals("A long text", treeModel.getValue("user.note"));
        assertEquals("''", treeModel.getValue("user.noteq"));
        assertEquals("https://test.txt", treeModel.getValue("user.url"));
     // assertEquals("", treeModel.getValue("user.note")); // TODO
        assertFalse(treeModel.toYaml().contains("Users"));
    }

    @Test
    public void toProps() {
        var yaml = """
            user:
              name: Růžena
              address:
                city: Paris
              age : 25
            """;

        var treeModel = TreeModel.ofYaml(yaml);
        var props = treeModel.toProps();

        assertTrue(props.contains("user.name = Růžena"));
        assertTrue(props.contains("user.age = 25"));
        assertTrue(props.contains("user.address.city = Paris"));

        assertEquals("Růžena", treeModel.getValue("user.name"));
        assertEquals("25", treeModel.getValue("user.age"));
        assertEquals("Paris", treeModel.getValue("user.address.city"));
    }

    @Test
    public void ofProps2() {
        var props = """
            user.name=Růžena
            user.age = 30
            user.address.city\t=\tLondon
            """;
        var treeModel = TreeModel.ofProps(props);

        assertEquals("Růžena", treeModel.getValue("user.name", ""));
        assertEquals("30", treeModel.getValue("user.age", ""));
        assertEquals("London", treeModel.getValue("user.address.city", ""));

        assertEquals(".", treeModel.getValue("user.aa", "."));
        assertEquals("test", treeModel.getValue("user.bb", "test"));
        assertNull(treeModel.getValue("user.cc", null));
    }

    @Test
    public void ofPropsWrong() {
        var props = """
            user = Růžena
            user.age = 30
            user.active = true
            """;

        var exception = assertThrows(IllegalArgumentException.class, () -> {
            TreeModel.ofProps(props);
        });

        assertEquals("Duplicated YAML key: 'user.active'", exception.getMessage());
    }


    // ----- Converters -----

    /** Convert YAML to PROPERTIES */
    public String convertYamlToProps(String yaml) {
        return new TreeModel().toProps();
    }

    /** Convert YAML to PROPERTIES */
    public String convertPropsToYaml(String properties) {
        return new TreeModel().toYaml();
    }

    @Test
    void testConvert() {
        var props = """
            user.address.city = London
            user.age = 30
            user.name = Růžena
            user.note = Long text
            user.url = https://test.txt
            """;

        var yaml = """
            user:
              address:
                city: London
              age: 30
              name: Růžena
              note: 'Long text'
              url: 'https://test.txt'
                """;

        var result1 = TreeModel.convertPropsToYaml(props);
        assertEquals(yaml, result1);

        var result2 = TreeModel.convertYamlToProps(yaml);
        assertEquals(props, result2);
    }

    @Test
    void toMap() {
        var yaml = """
            user:
              name: Růžena
              address:
                city: Paris
              age : 25
            """;

        var map = TreeModel.ofYaml(yaml).toMap();

        assertEquals("Růžena", map.get("user.name"));
        assertEquals("25", map.get("user.age"));
        assertEquals("Paris", map.get("user.address.city"));
    }
}
