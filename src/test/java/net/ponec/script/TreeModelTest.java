package net.ponec.script;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TreeModelTest {

    @Test
    public void testPropsToYamlConversion() {
        var props = """
            user.name=John
            user.age=30
            user.city=London
            """;

        var treeModel = TreeModel.parseProps(props);
        var yaml = treeModel.toYaml();

        assertTrue(yaml.contains("name: John"));
        assertTrue(yaml.contains("age: 30"));
        assertTrue(yaml.contains("city: London"));
    }

    @Test
    public void testYamlToPropsConversion() {
        var yaml = """
            user:
              name: Alice
              age: 25
              city: Paris
            """;

        var treeModel = TreeModel.parseYaml(yaml);
        var props = treeModel.toProps();

        assertTrue(props.contains("user.name=Alice"));
        assertTrue(props.contains("user.age=25"));
        assertTrue(props.contains("user.city=Paris"));
    }

    @Test
    public void testGetValueWithDefault() {
        var props = "config.mode=debug";
        var model = TreeModel.parseProps(props);
        assertEquals("debug", model.getValue("config.mode", "default"));
        assertEquals("default", model.getValue("config.unknown", "default"));
    }
}
