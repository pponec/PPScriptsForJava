/*
 *  Copyright 2024-2024 Pavel Ponec
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.ponec.script;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.ponec.script.SqlExecutor.SqlParamBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testing the SqlParamBuilder class
 * @author Pavel Ponec
 */
public class SqlParamBuilderTest extends AbstractJdbcConnector {

    /** Some testing date */
    private final LocalDate someDate = LocalDate.parse("2018-09-12");

    @Test
    public void testShowUsage() throws Exception {
        try (var dbConnection = createDbConnection())  {
            runSqlStatementTest(dbConnection);
            toStringTest_1(dbConnection);
            toStringTest_2(dbConnection);
        }
    }

    /** Example of SQL statement INSERT. */
    public void runSqlStatementTest(Connection dbConnection) throws SQLException {

        try (var builder = new SqlParamBuilder(dbConnection)) {
            System.out.println("CREATE TABLE");
            builder.sql("CREATE TABLE employee",
                            "( id INTEGER PRIMARY KEY",
                            ", name VARCHAR(256) DEFAULT 'test'",
                            ", code VARCHAR(1)",
                            ", created DATE NOT NULL",
                            ")")
                    .execute();

            System.out.println("SINGLE INSERT");
            builder.sql("INSERT INTO employee",
                            "( id, code, created ) VALUES",
                            "( :id, :code, :created )")
                    .bind("id", 1)
                    .bind("code", "T")
                    .bind("created", someDate)
                    .bind(false, "incorrectArgument", someDate)
                    .execute();

            System.out.println("MULTI INSERT");
            builder.sql("INSERT INTO employee",
                            "(id,code,created) VALUES",
                            "(:id1,:code,:created),",
                            "(:id2,:code,:created)")
                    .bind("id1", 2)
                    .bind("id2", 3)
                    .bind("code", "T")
                    .bind("created", someDate.plusDays(1))
                    .executeInsert();
            var lastId = builder.generatedLastKey(rs -> rs.getInt(1));
            Assertions.assertEquals(3, lastId);

            System.out.println("Previous statement with modified parameters");
            builder.bind("id1", 11)
                    .bind("id2", 12)
                    .bind("code", "V")
                    .execute();

            System.out.println("SELECT 1");
            List<Employee> employees = builder.sql("SELECT t.id, t.name, t.created",
                            "FROM employee t",
                            "WHERE t.id < :id",
                            "  AND t.code IN (:code)",
                            "ORDER BY t.id")
                    .bind("id", 10)
                    .bind("code", "T", "V")
                    .streamMap(rs -> new Employee(
                            rs.getInt(1),
                            rs.getString(2),
                            rs.getObject(3, LocalDate.class)))
                    .toList();

            assertEquals(3, employees.size());
            Assertions.assertEquals(1, employees.get(0).id);
            Assertions.assertEquals("test", employees.get(0).name);
            Assertions.assertEquals(someDate, employees.get(0).created);

            System.out.println("SELECT 2 (reuse the previous SELECT)");
            List<Employee> employees2 = builder
                    .bind("id", 100)
                    .streamMap(rs -> new Employee(
                            rs.getInt(1),
                            rs.getString(2),
                            rs.getObject(3, LocalDate.class)))
                    .toList();

            assertEquals(5, employees2.size());

            var counter = new AtomicInteger();
            builder.forEach(rs -> {
                var id = rs.getInt("id");
                System.out.printf("\tid = %s%n", id);
                counter.incrementAndGet();
            });
            assertEquals(5, counter.get());
            assertEquals("SELECT t.id, t.name, t.created" +
                            " FROM employee t" +
                            " WHERE t.id < [100] AND t.code IN ([T],[V])" +
                            " ORDER BY t.id",
                    builder.toStringLine());
        }
    }

    public void toStringTest_1(Connection dbConnection) {
        try (var builder = new SqlParamBuilder(dbConnection)) {

            System.out.println("MISSING PARAMS");
            builder.sql("SELECT t.id, t.name",
                    "FROM employee t",
                    "WHERE t.id > :id",
                    "  AND t.code = :code",
                    "ORDER BY t.id");
            Assertions.assertEquals(builder.sqlTemplate, builder.toString());

            var ex = Assertions.assertThrows(SqlParamBuilder.SqlException.class, () -> {
                var count = builder.streamMap(t -> t).count();
            });
            assertEquals("Missing SQL parameter: [code, id]", ex.getMessage());

            System.out.println("ASSIGNED PARAMS");
            builder.bind("id", 10);
            builder.bind("code", "w");
            String expected = """
                    SELECT t.id, t.name
                    FROM employee t
                    WHERE t.id > [10]
                      AND t.code = [w]
                    ORDER BY t.id""".stripIndent();
            assertEquals(expected, builder.toString());
        }
    }

    public void toStringTest_2(Connection dbConnection) {
        try (var builder = new SqlParamBuilder(dbConnection)) {
            var log = builder.sql("""
                            SELECT t.id, t.name
                            FROM employee t
                            WHERE t.name = 'a:\\{b}\\(c)'
                              AND t.code = :code
                              """)
                    .bind("code", "x:\\{y}\\(z)")
                    .toString();

            var expected = """
                    SELECT t.id, t.name
                    FROM employee t
                    WHERE t.name = 'a:\\{b}\\(c)'
                      AND t.code = [x:\\{y}\\(z)]
                    """;
            assertEquals(expected, log);
        }
    }

    record Employee (int id, String name,  LocalDate created){}

}
