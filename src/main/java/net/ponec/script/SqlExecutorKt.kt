/*
 * Common utilities for Java17+ for the CLI (command line interface).
 * Usage: see SqlExecutorKt.sh
 * Environment: Java 17+ with JDBC driver com.h2database:h2:2.2.224 are required.
 * Licence: Apache License, Version 2.0, Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 */
package net.ponec.scriptKt

import java.sql.*
import java.time.LocalDate
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Stream
import java.util.stream.StreamSupport

//KTS// SqlExecutorKt.main(args) // For the Kotlin script, don't remove it.

/** Use SQL statements by the SqlParamBuilder class.  */
class SqlExecutorKt {
    private val someDate = LocalDate.parse("2018-09-12")

    companion object Static {
        private val db = ConnectionProvider.forH2("user", "pwd")
        @JvmStatic @Throws(Exception::class)
        fun main(args: Array<String>) {
            println("args: [${args.joinToString()}]")
            db.connection().use { SqlExecutorKt().mainStart(it) }
        }
    }

    fun mainStart(dbConnection: Connection) {
        SqlParamBuilderKt(dbConnection).use { builder ->

            println("# CREATE TABLE")
            builder.sql("""
                            CREATE TABLE employee
                            ( id INTEGER PRIMARY KEY
                            , name VARCHAR(256) DEFAULT 'test'
                            , code VARCHAR(1)
                            , created DATE NOT NULL )
                            """.trimIndent())
                .execute()

            println("# SINGLE INSERT")
            builder.sql("""
                            INSERT INTO employee
                            ( id, code, created ) VALUES
                            ( :id, :code, :created )
                            """.trimIndent())
                .bind("id", 1)
                .bind("code", "T")
                .bind("created", someDate)
                .execute()

            println("# MULTI INSERT")
            builder.sql("""
                            INSERT INTO employee
                            (id,code,created) VALUES
                            (:id1,:code,:created),
                            (:id2,:code,:created)
                            """.trimIndent())
                .bind("id1", 2)
                .bind("id2", 3)
                .bind("code", "T")
                .bind("created", someDate.plusDays(7))
                .execute()
            builder.bind("id1", 11)
                .bind("id2", 12)
                .bind("code", "V")
                .execute()

            println("# SELECT")
            val employees: List<Employee> = builder.sql("""
                            SELECT t.id, t.name, t.created
                            FROM employee t
                            WHERE t.id < :id
                              AND t.code IN (:code)
                            ORDER BY t.id
                            """.trimIndent())
                .bind("id", 10)
                .bind("code", "T", "V")
                .streamMap { Employee(
                        it.getInt("id"),
                        it.getString("name"),
                        it.getObject("created", LocalDate::class.java)) }
                .toList()
            println("# PRINT RESULT OF: ${builder.toStringLine()}")
            employees.stream().forEach { employee: Employee -> println(employee) }
            assertEquals(3, employees.size)
            assertEquals(1, employees[0].id)
            assertEquals("test", employees[0].name)
            assertEquals(someDate, employees[0].created)
        }
    }

    data class Employee(val id: Int, val name: String, val created: LocalDate)

    internal data class ConnectionProvider(
        val jdbcClass: String,
        val jdbcUrl: String,
        val user: String,
        val passwd: String
    ) {
        fun connection(): Connection {
            return try {
                Class.forName(jdbcClass)
                DriverManager.getConnection(jdbcUrl, user, passwd)
            } catch (ex: ClassNotFoundException) {
                throw SQLException("Driver class not found: $jdbcClass", ex)
            }
        }

        companion object {
            fun forH2(user: String, passwd: String) = ConnectionProvider(
                "org.h2.Driver",
                "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
                user, passwd
            )
        }
    }

    private fun <T> assertEquals(expected: T, result: T) {
        check(expected == result) { "Objects are not equals: '${expected}' <> '${result}'" }
    }

    /**
     * Less than 130 lines long class to simplify work with JDBC.
     * Original source: [GitHub](https://github.com/pponec/DirectoryBookmarks/blob/development/src/main/java/net/ponec/script/SqlExecutor.java)
     * Licence: Apache License, Version 2.0
     * @author Pavel Ponec, https://github.com/pponec
     * @version 1.0.7
     */
    internal class SqlParamBuilderKt(private val connection: Connection) : AutoCloseable {
        private val params: MutableMap<String, Array<out Any?>> = HashMap()
        private val sqlParameterMark = Pattern.compile(":(\\w+)")
        var sqlTemplate: String = ""; private set
        private var preparedStatement: PreparedStatement? = null

        /** Close statement (if any) and set the new SQL template  */
        fun sql(vararg sqlLines: String): SqlParamBuilderKt {
            close()
            sqlTemplate = if (sqlLines.size == 1) sqlLines[0] else sqlLines.joinToString("\n")
            return this
        }

        /** Assign a SQL value(s). In case a reused statement set the same number of parameters items.  */
        fun bind(key: String, vararg values: Any?): SqlParamBuilderKt {
            params[key] = if (values.isNotEmpty()) values else arrayOfNulls(1)
            return this
        }

        fun execute(): Int =
            prepareStatement().executeUpdate()

        /** A ResultSet object is automatically closed when the Statement object that generated it is closed,
         * re-executed, or used to retrieve the next result from a sequence of multiple results.  */
        private fun executeSelect(): ResultSet {
            return try {
                prepareStatement().executeQuery()
            } catch (ex: Exception) {
                throw ex as? RuntimeException ?: IllegalStateException(ex)
            }
        }

        /** Use the  [.streamMap] or [.forEach] methods rather  */
        private fun stream(): Stream<ResultSet> {
            val resultSet = executeSelect()
            val iterator = object : Iterator<ResultSet> {
                override fun hasNext(): Boolean {
                    return try {
                        resultSet.next()
                    } catch (e: SQLException) {
                        throw IllegalStateException(e)
                    }
                }

                override fun next(): ResultSet {
                    return resultSet
                }
            }
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
        }

        /** Iterate executed select  */
        fun forEach(consumer: (ResultSet) -> Unit) =
            stream().forEach(consumer)

        fun <R> streamMap(mapper: (ResultSet) -> R): Stream<R> =
            stream().map(mapper)

        /** The method closes a PreparedStatement object with related objects, not the database connection.  */
        override fun close() {
            try {
                preparedStatement.use {} // AutoCloseable
            } catch (e: Exception) {
                throw IllegalStateException("Closing fails", e)
            } finally {
                preparedStatement = null
                params.clear()
            }
        }

        @Throws(SQLException::class)
        fun prepareStatement(): PreparedStatement {
            val sqlValues = mutableListOf<Any?>()
            val sql = buildSql(sqlValues, false)
            val result = preparedStatement ?: connection.prepareStatement(sql) ?: throw IllegalStateException()
            for (i in 0 until sqlValues.size) {
                result.setObject(i + 1, sqlValues[i])
            }
            preparedStatement = result
            return result
        }

        private fun buildSql(sqlValues: MutableList<Any?>, toLog: Boolean): String {
            val result = StringBuilder(256)
            val matcher = sqlParameterMark.matcher(sqlTemplate)
            val missingKeys = mutableSetOf<Any>()
            while (matcher.find()) {
                val key = matcher.group(1)
                val values = params[key]
                if (values != null) {
                    matcher.appendReplacement(result, "")
                    for (i in values.indices) {
                        if (i > 0) result.append(',')
                        result.append(Matcher.quoteReplacement(if (toLog) "[${values[i]}]" else "?"))
                        sqlValues.add(values[i])
                    }
                } else {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()))
                    missingKeys.add(key)
                }
            }
            require(toLog || missingKeys.isEmpty()) {
                "Missing value of the keys: [${missingKeys.joinToString (", ")}]" }
            matcher.appendTail(result)
            return result.toString()
        }

        override fun toString(): String {
            return buildSql(ArrayList(), true)
        }

        fun toStringLine(): String {
            return toString().replace("\\s*\\R+\\s*".toRegex(), " ")
        }
    }
}