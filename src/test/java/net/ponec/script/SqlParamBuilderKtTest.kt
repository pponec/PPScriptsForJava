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
package net.ponec.script

import net.ponec.scriptKt.SqlExecutorKt
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * Testing the SqlParamBuilder class
 * @author Pavel Ponec
 */
class SqlParamBuilderKtTest : AbstractJdbcConnector() {
    /** Some testing date  */
    private val someDate = LocalDate.parse("2018-09-12")
    @Test
    @Throws(Exception::class)
    fun testShowUsage() {
        createDbConnection().use { dbConnection ->
            runSqlStatementTest(dbConnection)
            toStringTest(dbConnection)
        }
    }

    /** Example of SQL statement INSERT.  */
    @Throws(SQLException::class)
    fun runSqlStatementTest(dbConnection: Connection?) {
        SqlExecutorKt.SqlParamBuilderKt(dbConnection!!).use { builder ->
            println("CREATE TABLE")
            builder.sql(
                "CREATE TABLE employee",
                "( id INTEGER PRIMARY KEY",
                ", name VARCHAR(256) DEFAULT 'test'",
                ", code VARCHAR(1)",
                ", created DATE NOT NULL",
                ")"
            )
                .execute()
            println("SINGLE INSERT")
            builder.sql(
                "INSERT INTO employee",
                "( id, code, created ) VALUES",
                "( :id, :code, :created )"
            )
                .bind("id", 1)
                .bind("code", "T")
                .bind("created", someDate)
                .execute()
            println("MULTI INSERT")
            builder.sql(
                "INSERT INTO employee",
                "(id,code,created) VALUES",
                "(:id1,:code,:created),",
                "(:id2,:code,:created)"
            )
                .bind("id1", 2)
                .bind("id2", 3)
                .bind("code", "T")
                .bind("created", someDate.plusDays(1))
                .execute()
            println("Previous statement with modified parameters")
            builder.bind("id1", 11)
                .bind("id2", 12)
                .bind("code", "V")
                .execute()
            println("SELECT 1")
            val employees = builder.sql(
                "SELECT t.id, t.name, t.created",
                "FROM employee t",
                "WHERE t.id < :id",
                "  AND t.code IN (:code)",
                "ORDER BY t.id"
            )
                .bind("id", 10)
                .bind("code", "T", "V")
                .streamMap { Employee(
                    it.getInt(1),
                    it.getString(2),
                    it.getObject(3, LocalDate::class.java))
                }
                .toList()
            Assertions.assertEquals(3, employees.size)
            Assertions.assertEquals(1, employees[0].id)
            Assertions.assertEquals("test", employees[0].name)
            Assertions.assertEquals(someDate, employees[0].created)
            println("SELECT 2 (reuse the previous SELECT)")
            val employees2 = builder
                .bind("id", 100)
                .streamMap { Employee(
                        it.getInt(1),
                        it.getString(2),
                        it.getObject(3, LocalDate::class.java))
                }
                .toList()
            Assertions.assertEquals(5, employees2.size)
            val counter = AtomicInteger()
            builder.forEach { rs: ResultSet? -> counter.incrementAndGet() }
            Assertions.assertEquals(5, counter.get())
            Assertions.assertEquals(
                "SELECT t.id, t.name, t.created" +
                        " FROM employee t" +
                        " WHERE t.id < [100] AND t.code IN ([T],[V])" +
                        " ORDER BY t.id",
                builder.toStringLine()
            )
        }
    }

    fun toStringTest(dbConnection: Connection) {
        SqlExecutorKt.SqlParamBuilderKt(dbConnection!!).use { builder ->
            println("MISSING PARAMS")
            builder.sql(
                "SELECT t.id, t.name",
                "FROM employee t",
                "WHERE t.id > :id",
                "  AND t.code = :code",
                "ORDER BY t.id"
            )
            Assertions.assertEquals(builder.sqlTemplate, builder.toString())
            val ex = Assertions.assertThrows(IllegalArgumentException::class.java) {
                val count = builder.streamMap { t: ResultSet? -> t }
                    .count()
            }
            Assertions.assertEquals("Missing value of the keys: [id, code]", ex.message)
            println("ASSIGNED PARAMS")
            builder.bind("id", 10)
            builder.bind("code", "w")
            val expected = """
                    SELECT t.id, t.name
                    FROM employee t
                    WHERE t.id > [10]
                      AND t.code = [w]
                    ORDER BY t.id
                    """.trimIndent().stripIndent()
            Assertions.assertEquals(expected, builder.toString())
        }
    }

    @JvmRecord
    internal data class Employee(val id: Int, val name: String, val created: LocalDate)
}
