/*
 * Common utilities for Java17+ for the CLI (command line interface).
 * Usage: java -cp ../lib/h2-2.2.224.jar SqlExecutor.java
 *
 * Environment: Java 17+ with JDBC driver com.h2database:h2:2.2.224 are required.
 * Licence: Apache License, Version 2.0, Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 */
package utils;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Use SQL statements by the SqlParamBuilder class. */
public final class SqlExecutor {

    private final static ConnectionProvider db = ConnectionProvider.forH2("user", "pwd");
    private final PrintStream out = System.out;

    public static void main(final String[] args) throws Exception {
        try (Connection connection = db.connection()) {
            new SqlExecutor().mainStart(connection, args);
        }
    }

    void mainStart(Connection connection, String... args) throws Exception {
        // Create DB table
        var createTable = """
                CREATE TABLE employee
                ( id INTEGER PRIMARY KEY
                , name VARCHAR(256) DEFAULT 'test'
                , code VARCHAR(1)
                , created DATE NOT NULL
                )""".stripIndent();
        try (var sql = new SqlParamBuilder(createTable, connection)) {
            sql.execute();
        }

        // DB insert
        var insertSql = """
                INSERT INTO employee
                ( id, code, created) VALUES
                ( :id1, :code, :created),
                ( :id2, :code, :created)
                """.stripIndent();
        var insertArgs = Map.of(
                "id1", 1,
                "id2", 2,
                "code", "T",
                "created", LocalDate.parse("2024-04-14"));
        try (var sql = new SqlParamBuilder(insertSql, insertArgs, connection)) {
            sql.execute();
            // Insert next two rows:
            sql.setParam("id1", 11).setParam("id2", 12).setParam("code", "V");
            sql.execute();
        }

        // Select
        String selectSql = """
                SELECT t.id, t.code, t.created
                FROM employee t
                WHERE t.id < :id
                  AND t.code IN (:code)
                ORDER BY t.id
                """.stripIndent();
        var selectArgs = Map.of("id", 10, "code", Arrays.asList("T", "V"));
        try (var sql = new SqlParamBuilder(selectSql, selectArgs, connection)) {
            for (var rs : sql.executeSelect()) {
                out.printf("id:%s, code:%s, created=%s %n".formatted(
                      rs.getInt(1), rs.getString(2), rs.getObject(3)));
            }
            // New SELECT with modified SQL arguments:
            sql.setParam("id", 100);
            for (var rs : sql.executeSelect()) {
                out.printf("id:%s, code:%s, created=%s %n".formatted(
                        rs.getInt(1), rs.getString(2), rs.getObject(3)));
            }
        }
    }

    /** A utility class from the Ujorm framework */
    static class SqlParamBuilder implements Closeable {

        /** SQL parameter mark type of {@code :param} */
        private static final Pattern SQL_MARK = Pattern.compile(":(\\w+)(?=[\\s,;\\]\\)]|$)");

        private final String sqlTemplate;

        private final Map<String, Object> params;

        private final Connection dbConnection;

        private PreparedStatement preparedStatement = null;

        private ResultSetWrapper rsWrapper = null;

        public SqlParamBuilder(
                 CharSequence sqlTemplate,
                 Map<String, ?> params,
                 Connection dbConnection) {
            this.sqlTemplate = sqlTemplate.toString();
            this.params = new HashMap<>(params);
            this.dbConnection = dbConnection;
        }

        public SqlParamBuilder( CharSequence sqlTemplate, Connection dbConnection) {
            this(sqlTemplate, new HashMap<>(), dbConnection);
        }

        public Iterable<ResultSet> executeSelect() throws IllegalStateException, SQLException {
            try (Closeable rs = rsWrapper) {
            } catch (IOException e) {
                throw new IllegalStateException("Closing fails", e);
            }
            rsWrapper = new ResultSetWrapper(prepareStatement().executeQuery());
            return rsWrapper;
        }

        public int execute() throws IllegalStateException, SQLException {
            return prepareStatement().executeUpdate();
        }

        public Connection getConnection() {
            return dbConnection;
        }

        /** The method closes a PreparedStatement object with related objects, not the database connection. */
        @Override
        public void close() {
            try (Closeable c1 = rsWrapper; PreparedStatement c2 = preparedStatement) {
            } catch (Exception e) {
                throw new IllegalStateException("Closing fails", e);
            } finally {
                rsWrapper = null;
                preparedStatement = null;
            }
        }

        public PreparedStatement prepareStatement() throws SQLException {
            final List<Object> sqlValues = new ArrayList<>();
            final String sql = buildSql(sqlValues, false);
            if (preparedStatement == null) {
                preparedStatement = dbConnection.prepareStatement(sql);
            }
            for (int i = 0, max = sqlValues.size(); i < max; i++) {
                preparedStatement.setObject(i + 1, sqlValues.get(i));
            }
            return preparedStatement;
        }

        protected String buildSql( List<Object> sqlValues, boolean toLog) {
            final StringBuilder result = new StringBuilder(256);
            final Matcher matcher = SQL_MARK.matcher(sqlTemplate);
            final Set<String> missingKeys = new HashSet<>();
            final Object[] singleValue = new Object[1];
            while (matcher.find()) {
                final String key = matcher.group(1);
                final Object value = params.get(key);
                if (value != null) {
                    matcher.appendReplacement(result, "");
                    singleValue[0] = value;
                    final Object[] values = value instanceof List ? ((List<?>) value).toArray() : singleValue;
                    for (int i = 0; i < values.length; i++) {
                        if (i > 0) result.append(',');
                        result.append(Matcher.quoteReplacement(toLog ? "[" + values[i] + "]" : "?"));
                        sqlValues.add(values[i]);
                    }
                } else {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(":" + key));
                    missingKeys.add(key);
                }
            }
            if (! toLog && !missingKeys.isEmpty()) {
                throw new IllegalArgumentException("Missing value of the keys: " + missingKeys);
            }
            matcher.appendTail(result);
            return result.toString();
        }

        /** Set a SQL parameter */
        public SqlParamBuilder setParam(String key, Object value) {
            this.params.put(key, value);
            return this;
        }

        @Override
        public String toString() {
            return buildSql(new ArrayList<>(), true);
        }

        /** Based on the {@code RowIterator} class of Ujorm framework. */
        static final class ResultSetWrapper implements Iterable<ResultSet>, Iterator<ResultSet>, Closeable {

            private ResultSet resultSet;
            /** It the cursor ready for reading? After a row reading the value will be set to false */
            private boolean cursorReady = false;
            /** Has a resultset a next row? */
            private boolean hasNext = false;

            public ResultSetWrapper( final ResultSet resultSet) {
                this.resultSet = resultSet;
            }

            @Override
            public Iterator<ResultSet> iterator() {
                return this;
            }

            @Override
            public Spliterator<ResultSet> spliterator() {
                throw new UnsupportedOperationException("Unsupported");
            }

            /** The last checking closes all resources. */
            @Override
            public boolean hasNext() throws IllegalStateException {
                if (!cursorReady) try {
                    hasNext = resultSet.next();
                    if (!hasNext) {
                        close();
                    }
                    cursorReady = true;
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
                return hasNext;
            }

            @Override
            public ResultSet next() {
                if (hasNext()) {
                    cursorReady = false;
                    return resultSet;
                }
                throw new NoSuchElementException();
            }

            @Override
            public void close() {
                try (ResultSet rs = resultSet) {
                    cursorReady = true;
                    hasNext = false;
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

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
}