/*
 * Common utilities for Java17+ for the CLI (command line interface).
 * Usage: java -cp ../lib/h2-2.2.224.jar SqlExecutorKtKt.java
 *
 * Environment: Java 17+ with JDBC driver com.h2database:h2:2.2.224 are required.
 * Licence: Apache License, Version 2.0, Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 */
package utils

import java.io.Closeable
import java.io.IOException
import java.io.Serializable
import java.sql.*
import java.time.LocalDate
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Use SQL statements by the SqlParamBuilder class.  */
class SqlExecutorKt {
    private val out = System.out

    companion object {
        private val db = ConnectionProvider2.forH2("user", "pwd")
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            db.connection().use { connection -> SqlExecutorKt().mainStart(connection, *args) }
        }
    }

    @Throws(Exception::class)
    fun mainStart(connection: Connection, vararg args: String?) {
        // Create DB table
        val createTable = """
                CREATE TABLE employee
                ( id INTEGER PRIMARY KEY
                , name VARCHAR(256) DEFAULT 'test'
                , code VARCHAR(1)
                , created DATE NOT NULL
                )
                """.trimIndent()
        SqlParamBuilder2(createTable, connection).use { sql -> sql.execute() }

        // DB insert
        val insertSql = """
                INSERT INTO employee
                ( id, code, created) VALUES
                ( :id1, :code, :created),
                ( :id2, :code, :created)
                """.trimIndent()

        val insertArgs: Map<String?, Serializable> = java.util.Map.of(
            "id1", 1,
            "id2", 2,
            "code", "T",
            "created", LocalDate.parse("2024-04-14")
        )
        SqlParamBuilder2(insertSql, insertArgs, connection).use { sql ->
            sql.execute()
            // Insert next two rows:
            sql.setParam("id1", 11).setParam("id2", 12).setParam("code", "V")
            sql.execute()
        }

        // Select
        val selectSql = """
                SELECT t.id, t.code, t.created
                FROM employee t
                WHERE t.id < :id
                  AND t.code IN (:code)
                ORDER BY t.id
                """.trimIndent()
        val selectArgs = java.util.Map.of("id", 10, "code", mutableListOf("T", "V"))
        SqlParamBuilder2(selectSql, selectArgs, connection).use { sql ->
            for (rs in sql.executeSelect()) {
                out.println(("id:${rs.getInt(1)}" +
                        ", code:${rs.getString(2)}" +
                        ", created=${rs.getObject(3)}")
                )
            }
            // New SELECT with modified SQL arguments:
            sql.setParam("id", 100)
            for (rs in sql.executeSelect()) {
                out.println(("id: ${rs.getInt(1)}" +
                        ", code:${rs.getString(2)}" +
                        ", created=${rs.getObject(3)}")
                )
            }
        }
    }
}

/** A utility class from the Ujorm framework  */
internal class SqlParamBuilder2(
    sqlTemplate: CharSequence,
    params: Map<String?, *>?,
    dbConnection: Connection
) : Closeable {
    private val sqlTemplate: String
    private val params: MutableMap<String?, Any>
    val connection: Connection
    private var preparedStatement: PreparedStatement? = null
    private var rsWrapper: ResultSetWrapper? = null

    init {
        this.sqlTemplate = sqlTemplate.toString()
        this.params = HashMap(params)
        connection = dbConnection
    }

    constructor(sqlTemplate: CharSequence, dbConnection: Connection) : this(
        sqlTemplate,
        HashMap<String?, Any>(),
        dbConnection
    )

    @Throws(IllegalStateException::class, SQLException::class)
    fun executeSelect(): Iterable<ResultSet> {
        try {
            rsWrapper.use { rs -> }
        } catch (e: IOException) {
            throw IllegalStateException("Closing fails", e)
        }
        rsWrapper = ResultSetWrapper(prepareStatement().executeQuery())
        return rsWrapper as Iterable<ResultSet>
    }

    @Throws(IllegalStateException::class, SQLException::class)
    fun execute(): Int {
        return prepareStatement().executeUpdate()
    }

    /** The method closes a PreparedStatement object with related objects, not the database connection.  */
    override fun close() {
        try {
            rsWrapper.use { c1 -> preparedStatement.use { c2 -> } }
        } catch (e: Exception) {
            throw IllegalStateException("Closing fails", e)
        } finally {
            rsWrapper = null
            preparedStatement = null
        }
    }

    @Throws(SQLException::class)
    fun prepareStatement(): PreparedStatement {
        val sqlValues: MutableList<Any> = ArrayList()
        val sql = buildSql(sqlValues, false)
        val result = preparedStatement ?: connection.prepareStatement(sql) ?: throw IllegalStateException()
        preparedStatement = result

        var i = 0
        val max = sqlValues.size
        while (i < max) {
            result.setObject(i + 1, sqlValues[i])
            i++
        }
        return result
    }

    protected fun buildSql(sqlValues: MutableList<Any>, toLog: Boolean): String {
        val result = StringBuilder(256)
        val matcher = SQL_MARK.matcher(sqlTemplate)
        val missingKeys: MutableSet<String> = HashSet()
        val singleValue = arrayOfNulls<Any>(1)
        while (matcher.find()) {
            val key = matcher.group(1)
            val value = params[key]
            if (value != null) {
                matcher.appendReplacement(result, "")
                singleValue[0] = value
                val values = if (value is List<*>) value.toTypedArray() else singleValue
                for (i in values.indices) {
                    if (i > 0) result.append(',')
                    result.append(Matcher.quoteReplacement(if (toLog) "[" + values[i] + "]" else "?"))
                    sqlValues.add(values[i] ?: "")
                }
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(":$key"))
                missingKeys.add(key)
            }
        }
        require(!(!toLog && !missingKeys.isEmpty())) { "Missing value of the keys: $missingKeys" }
        matcher.appendTail(result)
        return result.toString()
    }

    /** Set a SQL parameter  */
    fun setParam(key: String?, value: Any): SqlParamBuilder2 {
        params[key] = value
        return this
    }

    override fun toString(): String {
        return buildSql(ArrayList(), true)
    }

    /** Based on the `RowIterator` class of Ujorm framework.  */
    internal class ResultSetWrapper(private val resultSet: ResultSet) : Iterable<ResultSet>,
        Iterator<ResultSet>, Closeable {
        /** It the cursor ready for reading? After a row reading the value will be set to false  */
        private var cursorReady = false

        /** Has a resultset a next row?  */
        private var hasNext = false
        override fun iterator(): Iterator<ResultSet> {
            return this
        }

        override fun spliterator(): Spliterator<ResultSet> {
            throw UnsupportedOperationException("Unsupported")
        }

        /** The last checking closes all resources.  */
        @Throws(IllegalStateException::class)
        override fun hasNext(): Boolean {
            if (!cursorReady) try {
                hasNext = resultSet.next()
                if (!hasNext) {
                    close()
                }
                cursorReady = true
            } catch (e: SQLException) {
                throw IllegalStateException(e)
            }
            return hasNext
        }

        override fun next(): ResultSet {
            if (hasNext()) {
                cursorReady = false
                return resultSet
            }
            throw NoSuchElementException()
        }

        override fun close() {
            try {
                resultSet.use { rs ->
                    cursorReady = true
                    hasNext = false
                }
            } catch (e: SQLException) {
                throw IllegalStateException(e)
            }
        }
    }

    companion object {
        /** SQL parameter mark type of `:param`  */
        private val SQL_MARK = Pattern.compile(":(\\w+)(?=[\\s,;\\]\\)]|$)")
    }
}

internal data class ConnectionProvider2(
    val jdbcClass: String,
    val jdbcUrl: String,
    val user: String,
    val passwd: String
) {
    @Throws(SQLException::class)
    fun connection(): Connection {
        return try {
            Class.forName(jdbcClass)
            DriverManager.getConnection(jdbcUrl, user, passwd)
        } catch (ex: ClassNotFoundException) {
            throw SQLException("Driver class not found: $jdbcClass", ex)
        }
    }

    companion object {
        fun forH2(user: String, passwd: String) = ConnectionProvider2(
                "org.h2.Driver",
                "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
                user, passwd
            )
    }
}
