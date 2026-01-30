/*
 * Common utilities for Java17+ for the CLI (command line interface).
 * Usage: java -cp ../lib/h2-2.2.224.jar SqlExecutor.java
 *
 * Environment: Java 17+ with JDBC driver com.h2database:h2:2.2.224 are required.
 * Licence: Apache License, Version 2.0, Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 */
package net.ponec.script;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Use SQL statements by the SqlParamBuilder class. */
public final class SqlExecutor {
    private final static ConnectionProvider db = ConnectionProvider.forH2("user", "pwd");
    private final static LocalDate someDate = LocalDate.parse("2020-09-24");

    public static void main(final String[] args) throws Exception {
        System.out.println("Arguments: " + List.of(args));
        try (var dbConnection = db.connection()) {
            new SqlExecutor().mainStart(dbConnection);
        }
    }

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

            System.out.println("# PRINT RESULT OF: " + builder.toStringLine());
            employees.forEach(employee -> System.out.println(employee));
            assertEquals(3, employees.size());
            assertEquals(1, employees.get(0).id);
            assertEquals("test", employees.get(0).name);
            assertEquals(someDate, employees.get(0).created);
        }
    }

    record Employee (int id, String name, LocalDate created) {}

    record ConnectionProvider(String jdbcClass, String jdbcUrl, String user, String passwd) {

        Connection connection() throws SQLException {
            try {
                Class.forName(jdbcClass);
                return DriverManager.getConnection(jdbcUrl, user, passwd);
            } catch (ClassNotFoundException ex) {
                throw new SQLException("Driver class not found: " + jdbcClass, ex);
            }
        }

        public static ConnectionProvider forH2(String user, String passwd) {
            return new ConnectionProvider("org.h2.Driver",
                    "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
                    user, passwd);
        }
    }

    private <T> void assertEquals(T expected, T result) {
        if (!Objects.equals(expected, result)) {
            throw new IllegalStateException("Objects are not equals: '%s' <> '%s'".formatted(expected, result));
        }
    }

    /**
     * Less than 250 lines long class to simplify work with JDBC.
     * Original source: <a href="https://github.com/pponec/PPScriptsForJava/blob/development/src/main/java/net/ponec/script/SqlExecutor.java">GitHub</a>
     * Licence: Apache License, Version 2.0
     * @author Pavel Ponec, https://github.com/pponec
     * @version 1.1.3
     */
    static public class SqlParamBuilder implements AutoCloseable {
        /** SQL parameter mark type of {@code :param} */
        static final Pattern SQL_MARK = Pattern.compile(":(\\w+)");
        private final Connection dbConnection;
        protected String sqlTemplate = "";
        private final Map<String, ParamValue> params = new HashMap<>();
        private PreparedStatement preparedStatement = null;
        private ResultSet resultSet = null;

        public SqlParamBuilder(Connection dbConnection) {
            this.dbConnection = dbConnection;
        }

        /** Sets a new SQL template and resets current parameters. Any existing resources are closed. */
        public SqlParamBuilder sql(String... sqlLines) {
            close();
            params.clear();
            sqlTemplate = sqlLines.length == 1 ? sqlLines[0] : String.join("\n", sqlLines);
            return this;
        }

        /** Assigns SQL parameter values. If reusing a statement, ensure the same number of parameters is set. */
        public SqlParamBuilder bind(final String key, final Object... values) {
            return bind(true, key, values);
        }

        /** Assigns SQL parameter values. If reusing a statement, ensure the same number of parameters is set. */
        public SqlParamBuilder bind(final boolean enabled, final String key, final Object... values) {
            return bindObject(enabled, key, JDBCType.OTHER, values);
        }

        /** Assigns SQL parameter values. If reusing a statement, ensure the same number of parameters is set. */
        public SqlParamBuilder bindObject(final boolean enabled, final String key, final JDBCType jdbcType, final Object... values) {
            if (enabled) {
                params.put(key, new ParamValue(jdbcType, values));
            }
            return this;
        }

        public int execute() {
            try {
                return prepareStatement(Statement.NO_GENERATED_KEYS).executeUpdate();
            } catch (SQLException e) {
                throw new SqlException(e);
            }
        }

        /** Executes an INSERT statement with the ability to retrieve generated keys. */
        public int executeInsert() {
            try {
                return prepareStatement(Statement.RETURN_GENERATED_KEYS).executeUpdate();
            } catch (SQLException e) {
                throw new SqlException(e);
            }
        }

        /** Internal execution of a SELECT query. */
        private ResultSet executeSelect() {
            try {
                return prepareStatement(Statement.NO_GENERATED_KEYS).executeQuery();
            } catch (SQLException e) {
                throw new SqlException(e);
            }
        }

        /** Creates a Stream from the ResultSet. The Stream ensures the ResultSet is closed when finished. <br/>
         * Prefer {@link #streamMap(SqlFunction)} or {@link #forEach(SqlConsumer)}. */
        private Stream<ResultSet> stream(final ResultSet rs) {
            switchResultSet(rs);
            final var iterator = new Iterator<ResultSet>() {
                @Override
                public boolean hasNext() {
                    try {
                        return resultSet.next();
                    } catch (SQLException e) {
                        throw new SqlException(e);
                    }
                }
                @Override
                public ResultSet next() {
                    return rs;
                }
            };
            final var spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED);
            return StreamSupport.stream(spliterator, false).onClose(() -> switchResultSet(null));
        }

        /** Safely closes the current ResultSet and starts tracking the new one. */
        private void switchResultSet(final ResultSet rs) {
            try (var oldResultSet = this.resultSet) {
            } catch (SQLException e) {
                throw new SqlException(e);
            }
            this.resultSet = rs;
        }

        /** Executes the query and processes each row using the provided consumer. */
        public void forEach(SqlConsumer<ResultSet> consumer) throws SQLException {
            stream(executeSelect()).forEach(consumer);
        }

        /** Executes the query and returns a Stream of mapped results. */
        public <R> Stream<R> streamMap(SqlFunction<ResultSet, ? extends R> mapper) {
            return stream(executeSelect()).map(mapper);
        }

        /** Closes the PreparedStatement and any active ResultSet. The database connection remains open. */
        @Override
        public void close() {
            try (var ps = preparedStatement; var rs = resultSet) {
            } catch (Exception e) {
                throw new SqlException(e, "Closing resources failed");
            } finally {
                resultSet = null;
                preparedStatement = null;
                params.clear();
            }
        }

        /** Builds or reuses a PreparedStatement and binds current parameters.
         * @param autoGeneratedKeys For example: {@code Statement.RETURN_GENERATED_KEYS} */
        public PreparedStatement prepareStatement(int autoGeneratedKeys) {
            try {
                final var sqlValues = new ArrayList<ParamValue>(params.size());
                final var sql = buildSql(sqlValues, false);
                final var result = preparedStatement != null
                        ? preparedStatement
                        : dbConnection.prepareStatement(sql, autoGeneratedKeys);
                for (int i = 0, max = sqlValues.size(); i < max; i++) {
                    var sqlValue = sqlValues.get(i);
                    result.setObject(i + 1, sqlValue.first(), sqlValue.jdbcType);
                }
                preparedStatement = result;
                return result;
            } catch (SQLException e) {
                throw new SqlException(e, "prepareStatement()");
            }
        }

        protected ResultSet generatedKeysRs() {
            try {
                return preparedStatement != null ? preparedStatement.getGeneratedKeys() : null;
            } catch (SQLException e) {
                throw new SqlException(e, "generatedKeysRs()");
            }
        }

        /** Method for retrieving the primary keys of an INSERT statement. Only one call per INSERT is allowed. <br>
         * Usage: {@code builder.generatedKeys(rs -> rs.getInt(1)).findFirst()} */
        public <R> Stream<R> generatedKeys(SqlFunction<ResultSet, ? extends R> mapper) {
            final var generatedKeysRs = generatedKeysRs();
            return generatedKeysRs != null
                    ? stream(generatedKeysRs).map(mapper)
                    : Stream.of();
        }

        /** Method returns the last inserted key of the last INSERT statement.
         * @throws NoSuchElementException If no key found */
        public <R> R generatedLastKey(SqlFunction<ResultSet, ? extends R> mapper) throws NoSuchElementException {
            return generatedKeys(mapper).reduce((first, second) -> second)
                    .orElseThrow(() -> new NoSuchElementException("No keys"));
        }

        protected String buildSql(List<ParamValue> sqlValues, boolean toLog) {
            final var result = new StringBuffer(256);
            final var matcher = SQL_MARK.matcher(sqlTemplate);
            final var missingKeys = new HashSet<>();
            while (matcher.find()) {
                final var key = matcher.group(1);
                final var param = params.get(key);
                if (param != null) {
                    matcher.appendReplacement(result, "");
                    for (int i = 0; i < param.values.length; i++) {
                        if (i > 0) result.append(',');
                        result.append(toLog ? "[" + param.values[i] + "]" : "?");
                        sqlValues.add(i == 0 ? param : new ParamValue(param.jdbcType, param.values[i]));
                    }
                } else {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                    missingKeys.add(key);
                }
            }
            if (!toLog && !missingKeys.isEmpty()) {
                throw new SqlException(null, "Missing SQL parameter: " + missingKeys);
            }
            matcher.appendTail(result);
            return result.toString();
        }

        record ParamValue(JDBCType jdbcType, Object... values) {
            public Object first() {
                return values.length > 0 ? values[0] : null;
            }
        }

        @Override
        public String toString() {
            return buildSql(new ArrayList<>(), true);
        }

        public String toStringLine() {
            return toString().replaceAll("\\s*\\R+\\s*", " ");
        }

        @FunctionalInterface
        public interface SqlFunction<T, R> extends Function<T, R> {
            default R apply(T resultSet) {
                try {
                    return applyRs(resultSet);
                } catch (Exception ex) {
                    throw (ex instanceof RuntimeException re) ? re : new IllegalStateException(ex);
                }
            }
            R applyRs(T resultSet) throws SQLException;
        }

        @FunctionalInterface
        public interface SqlConsumer<T> extends Consumer<T> {
            @Override
            default void accept(final T t) {
                try {
                    acceptResultSet(t);
                } catch (Exception ex) {
                    throw (ex instanceof RuntimeException re) ? re : new IllegalStateException(ex);
                }
            }
            void acceptResultSet(T t) throws Exception;
        }

        public static final class SqlException extends IllegalStateException {
            private SqlException(Throwable cause, String... messages) {
                super((messages.length > 0 || cause == null)
                        ? String.join(" ", messages)
                        : cause.getMessage(), cause);
            }
        }
    }
}