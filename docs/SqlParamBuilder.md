# SqlParamBuilder

Script template for working with relational database using JDBC.

## The sample of usage

If you want to run the Java class as a script, you need to download and connect the JDBC driver.
You can use a Bash script `SqlExecutor.sh` that downloads the JDBC driver for the H2 database for for demonstration purposes.

```java
void mainStart(Connection dbConnection) throws Exception {
    try (SqlParamBuilder builder = new SqlParamBuilder(dbConnection)) {
        System.out.println("# CREATE TABLE");
        builder.sql("""
                        CREATE TABLE employee
                        ( id INTEGER PRIMARY KEY
                        , name VARCHAR(256) DEFAULT 'test'
                        , code VARCHAR(1)
                        , created DATE NOT NULL )
                        """)
                .execute();

        System.out.println("# SINGLE INSERT");
        builder.sql("""
                        INSERT INTO employee
                        ( id, code, created ) VALUES
                        ( :id, :code, :created )
                        """)
                .bind("id", 1)
                .bind("code", "T")
                .bind("created", someDate)
                .execute();

        System.out.println("# MULTI INSERT");
        builder.sql("""
                        INSERT INTO employee
                        (id,code,created) VALUES
                        (:id1,:code,:created),
                        (:id2,:code,:created)
                        """)
                .bind("id1", 2)
                .bind("id2", 3)
                .bind("code", "T")
                .bind("created", someDate.plusDays(7))
                .execute();
        builder.bind("id1", 11)
                .bind("id2", 12)
                .bind("code", "V")
                .execute();

        System.out.println("# SELECT");
        List<Employee> employees = builder.sql("""
                        SELECT t.id, t.name, t.created
                        FROM employee t
                        WHERE t.id < :id
                          AND t.code IN (:code)
                        ORDER BY t.id
                        """)
                .bind("id", 10)
                .bind("code", "T", "V")
                .streamMap(rs -> new Employee(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getObject("created", LocalDate.class)))
                .toList();
        System.out.printf("# PRINT RESULT OF: %s%n", builder.toStringLine());
        employees.stream().forEach((Employee employee) -> System.out.println(employee));

        assertEquals(3, employees.size());
        assertEquals(1, employees.get(0).id);
        assertEquals("test", employees.get(0).name);
        assertEquals(someDate, employees.get(0).created);
    }
}

record Employee (int id, String name, LocalDate created) {}

static class SqlParamBuilder {…}
```

The result of only one `SqlParamBuilder` object can be processed at a time. 
For multiple simultaneous processing, multiple objects of the `SqlParamBuilder` type must be opened.
For more information see a source code: [SqlExecutor.java](../src/main/java/net/ponec/script/SqlExecutor.java) .