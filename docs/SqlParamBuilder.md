# 170 lines of Java code to run SQL statements

I'd like to introduce you to a Java class with less than 170 lines of code to make it easier to work with SQL queries called through the JDBC API. 
What makes it special? 
The class can be embedded in a Java version 17 script.

The advantage of these scripts is that they are easily portable in text format and can be run without prior compilation, while having a fairly extensive Java standard library at our disposal.
The scripts can be used in various prototypes, in which it is possible (after connecting to the database) to solve more complicated data exports or data conversions.
Scripts are useful wherever we don't want (or can't) embed the implementation in a standard Java project.

However, using a script has some limitations, for example, the code must be written to a single file.
We can include all the libraries we need when we run the script, but they will probably have additional dependencies and simply listing them on the command line can be frustrating.
The complications associated with distributing such a script probably need not be stressed.
For these reasons, I believe that external libraries in scripts are best avoided.
If we still want to go the script route, the choice falls on pure JDBC.
For writing SQL queries, multi-line text literals can be preferably used, and the automatic closing of objects of the [PreparedStatement](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/PreparedStatement.html) type (implementing the [AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html) interface) also contributes to easier writing.
So what is the problem?

It is a good practice to map SQL parameter values to question marks.
I consider the main handicap of JDBC to be the **mapping of parameters** to a sequential question mark number (starting with a one).
The first version of mapping parameters to an SQL script often turns out well, but the risk of error increases as the number of parameters and additional SQL modifications increase.
As a reminder, inserting a new parameter in the first position requires renumbering the next row.
Another complication is the use of the IN operator, because for each enumeration value, a question mark must be written in the SQL template and mapped to a separate parameter.
If the parameter list is dynamic, the enumeration of question marks in the template must be dynamic as well.
Debugging more complex SQL can start taking significant time.

We'll have to wait a little longer to implement SQL parameter insertion using [String Templates](https://openjdk.org/jeps/459#Safely-composing-and-executing-database-queries) in Java 22.
However, inserting SQL parameters could be made easier by a simple wrapper over the PreparedStatement interface that would append parameters using JPA-style named tags (i.e., as alphanumeric text starting with a colon) when the statement is called.
Reading data from the database would be simplified by an API that allows chaining the necessary methods into a single statement with an output object of type Stream<ResultSet>.
For debugging or logging queries, a method for visualizing the SQL statement including the attached parameters would be useful.
I present you the `SqlParamBuilder` class.
The implementation priority was to cover the above requirements with a single Java class with minimalistic code.
The programming interface was inspired by the JDBI library.
The examples use the H2 database in in-memory mode.
However, connecting a database driver will be necessary.


```java
void mainStart(Connection dbConnection) throws Exception {
    try (var builder = new SqlParamBuilder(dbConnection)) {
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
        employees.forEach(employee -> System.out.println(employee));
        assertEquals(3, employees.size());
        assertEquals(1, employees.get(0).id);
        assertEquals("test", employees.get(0).name);
        assertEquals(someDate, employees.get(0).created);
    }
}

record Employee (int id, String name, LocalDate created) {}

static class SqlParamBuilder {…}
```

I'll add just a few notes to the usage demonstration:

- An instance of the SqlParamBuilder type can be recycled for multiple SQL statements.
The sql() method automatically closes the internal PrepradedStatement object (if one was previously open).
- After the statement is called, the parameters can be changed and the statement can be run again.
However, if you are changing a group of parameters (typically for the IN operator), you must send the same number for the same PreparedStatement object, otherwise an error will occur during parameter appending.
- The SqlParamBuilder object must be explicitly closed after the last command execution.
However, since we are implementing the AutoCloseable interface, it is sufficient to enclose the entire block in a try block.
The closure does not affect the contained database connection.
- In the Bash shell, the sample can be run with the `SqlExecutor.sh` script, which can download the JDBC driver for the H2 database.
- If you prefer Kotlin, you can try the Bash script `SqlExecutorKt.sh`, which calls a script written in Kotlin.
- Don't be fooled by the fact that the class is stored in a Maven project.
One of the reasons to use Maven is the ease of running jUnit tests.

The fastest way to create your own implementation is probably to download a sample script, rework the mainRun() method, and modify the connection parameters to your own database.
When running, be sure to connect the necessary JDBC driver.
A link to a sample use of the `SqlParamBuilder` class [is here](../src/main/java/net/ponec/script/SqlExecutor.java).
The class can be run using the Bash script [SqlExecutor.sh](../src/main/java/net/ponec/script/SqlExecutor.sh).
