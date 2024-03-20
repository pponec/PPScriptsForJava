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

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import net.ponec.script.SqlExecutor.SqlParamBuilder;


/**
 * Testing the SqlParamBuilder class
 * @author Pavel Ponec
 */
public class SqlParamBuilderDemoTest extends AbstractJdbcConnector {

    /** Some testing date */
    private final LocalDate someDate = LocalDate.parse("2018-09-12");
    private final LocalDate from = someDate.minusDays(1);
    private final LocalDate to = someDate.plusDays(2);

    @Test
    public void testShowUsage() throws Exception {
        try (Connection dbConnection = createDbConnection())  {
            runSqlStatements(dbConnection);
            runJdbcStatements(dbConnection);
        }
    }

    /** Example of SqlParamBuilder */
    public void runSqlStatements(Connection dbConnection) throws SQLException {
        try (var builder = new SqlParamBuilder(dbConnection)) {
            builder.sql("""
                            SELECT t.id, t.name, t.created
                            FROM employee t
                            WHERE t.id <= :id
                              AND t.created BETWEEN :dateFrom AND :dateTo
                              AND t.code IN (:code)
                            ORDER BY t.id""")
                    .bind("id", 2)
                    .bind("dateFrom", from)
                        .bind("dateTo", to)
                    .bind("code", "A", "B", "C", "D");
            List<Employee> employees1 = builder.streamMap(resultSet -> new Employee(
                    resultSet.getInt(1),
                    resultSet.getString(2),
                    resultSet.getObject(3, LocalDate.class)))
                    .toList();

            assertEquals(2, employees1.size());
            assertEquals("Employee[id=1, name=test, created=2018-09-12]", employees1.get(0).toString());
            assertEquals("""
                    SELECT t.id, t.name, t.created
                    FROM employee t
                    WHERE t.id <= [2]
                      AND t.created BETWEEN [2018-09-11] AND [2018-09-14]
                      AND t.code IN ([A],[B],[C],[D])
                    ORDER BY t.id
                    """.trim(), builder.toString());
        }
    }

    /** Example of JDBC */
    public void runJdbcStatements(Connection dbConnection) throws SQLException {
        try (var statement = dbConnection.prepareStatement("""
                            SELECT t.id, t.name, t.created
                            FROM employee t
                            WHERE t.id <= ?
                              AND t.created BETWEEN ? AND ?
                              AND t.code IN (?, ?, ?, ?)
                            ORDER BY t.id""")) {
            statement.setObject(1, 2);
            statement.setObject(2, from);
            statement.setObject(3, to);
            statement.setObject(4, "A");
            statement.setObject(5, "B");
            statement.setObject(6, "C");
            statement.setObject(7, "D");
            var employees = new ArrayList<>();
            var resultSet = statement.executeQuery();
            while (resultSet.next()) {
                new Employee(
                        resultSet.getInt(1),
                        resultSet.getString(2),
                        resultSet.getObject(3, LocalDate.class)
                );
            }
            System.out.println("> employess: " + employees);
        }
    }

    record Employee (int id, String name,  LocalDate created){}

    @Override
    protected Connection createDbConnection() throws ClassNotFoundException, SQLException {
        var result = super.createDbConnection();
        try (var builder = new SqlParamBuilder(result)) {
            builder.sql("CREATE TABLE employee",
                            "( id INTEGER PRIMARY KEY",
                            ", name VARCHAR(256) DEFAULT 'test'",
                            ", code VARCHAR(1)",
                            ", created DATE NOT NULL",
                            ")")
                    .execute();
            builder.sql("INSERT INTO employee",
                            "( id, code, created ) VALUES",
                            "( :id, :code, :created )");
            builder.bind("id",  1).bind("code", "A").bind("created", someDate).execute();
            builder.bind("id",  2).bind("code", "B").bind("created", someDate).execute();
            builder.bind("id",  3).bind("code", "C").bind("created", someDate).execute();
            builder.bind("id", 11).bind("code", "D").bind("created", someDate).execute();
            builder.bind("id", 12).bind("code", "E").bind("created", someDate).execute();
            builder.bind("id", 13).bind("code", "F").bind("created", someDate).execute();

        }
        return result;
    }
}
