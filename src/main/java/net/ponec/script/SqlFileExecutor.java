/*
 * Common utilities for Java17+ for the CLI (command line interface).
 * Usage: java -cp ../lib/h2-2.2.224.jar SqlExecutor.java
 *
 * Environment: Java 17+ with JDBC driver com.h2database:h2:2.2.224 are required.
 * Licence: Apache License, Version 2.0, Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 */
package net.ponec.script;

import java.nio.file.Files;
import java.nio.file.Path;
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
public final class SqlFileExecutor {
    private final static ConnectionProvider db = ConnectionProvider.forH2("user", "pwd");
    private final static LocalDate someDate = LocalDate.parse("2020-09-24");

    public static void main(final String[] args) throws Exception {
        System.out.println("Arguments: " + List.of(args));
        try (var dbConnection = db.connection()) {
            new SqlFileExecutor().mainStart(dbConnection);
        }
    }

    void mainStart(Connection dbConnection) throws Exception {
        try (var builder = new SqlParamBuilder(dbConnection)) {
            builder.sql(Files.readString(Path.of("insert.sql")))
                    .bind("id", 1)
                    .bind("code", "T")
                    .bind("created", someDate)
                    .execute();

            List<Employee> employees = builder
                    .sql(Files.readString(Path.of("select.sql")))
                    .bind("id", 10)
                    .bind("code", "T", "V")
                    .streamMap(rs -> new Employee(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getObject("created", LocalDate.class)))
                    .toList();
        }
    }

    record Employee (int id, String name, LocalDate created) {}
    //static class SqlParamBuilder {…}//static class SqlParamBuilder {…}

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
     * Less than 170 lines long class to simplify work with JDBC.
     * Original source: <a href="https://github.com/pponec/PPScriptsForJava/blob/development/src/main/java/net/ponec/script/SqlExecutor.java">GitHub</a>
     * Licence: Apache License, Version 2.0
     * @author Pavel Ponec, https://github.com/pponec
     * @version 1.0.9
     */
    static class SqlParamBuilder implements AutoCloseable {
        /** SQL parameter mark type of {@code :param} */
        private static final Pattern SQL_MARK = Pattern.compile(":(\\w+)");
        private final Connection dbConnection;
        private final Map<String, Object[]> params = new HashMap<>();
        protected String sqlTemplate = "";
        private PreparedStatement preparedStatement = null;

        public SqlParamBuilder(Connection dbConnection) {
            this.dbConnection = dbConnection;
        }

        /** Close statement (if any) and set the new SQL template */
        public SqlParamBuilder sql(String... sqlLines) {
            close();
            sqlTemplate = sqlLines.length == 1 ? sqlLines[0] : String.join("\n", sqlLines);
            return this;
        }

        /** Assign a SQL value(s). In case a reused statement set the same number of parameters items. */
        public SqlParamBuilder bind(String key, Object... values) {
            params.put(key, values.length > 0 ? values : new Object[]{null});
            return this;
        }

        public int execute() throws IllegalStateException, SQLException {
            return prepareStatement().executeUpdate();
        }

        /** A ResultSet object is automatically closed when the Statement object that generated it is closed,
          * re-executed, or used to retrieve the next result from a sequence of multiple results. */
        private ResultSet executeSelect() throws IllegalStateException {
            try {
                return prepareStatement().executeQuery();
            } catch (Exception ex) {
                throw (ex instanceof RuntimeException re) ? re : new IllegalStateException(ex);
            }
        }

        /** Use the  {@link #streamMap(SqlFunction)} or {@link #forEach(SqlConsumer)} methods rather */
        private Stream<ResultSet> stream() {
            final var resultSet = executeSelect();
            final var iterator = new Iterator<ResultSet>() {
                @Override
                public boolean hasNext() {
                    try {
                        return resultSet.next();
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                }
                @Override
                public ResultSet next() {
                    return resultSet;
                }
            };
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
        }

        /** Iterate executed select */
        public void forEach(SqlConsumer<ResultSet> consumer) throws IllegalStateException  {
            stream().forEach(consumer);
        }

        public <R> Stream<R> streamMap(SqlFunction<ResultSet, ? extends R> mapper ) {
            return stream().map(mapper);
        }

        /** The method closes a PreparedStatement object with related objects, not the database connection. */
        @Override
        public void close() {
            try (AutoCloseable c2 = preparedStatement) {
            } catch (Exception e) {
                throw new IllegalStateException("Closing fails", e);
            } finally {
                preparedStatement = null;
                params.clear();
            }
        }

        public PreparedStatement prepareStatement() throws SQLException {
            final var sqlValues = new ArrayList<>();
            final var sql = buildSql(sqlValues, false);
            final var result = preparedStatement != null
                    ? preparedStatement
                    : dbConnection.prepareStatement(sql);
            for (int i = 0, max = sqlValues.size(); i < max; i++) {
                result.setObject(i + 1, sqlValues.get(i));
            }
            preparedStatement = result;
            return result;
        }

        private String buildSql(List<Object> sqlValues, boolean toLog) {
            final var result = new StringBuilder(256);
            final var matcher = SQL_MARK.matcher(sqlTemplate);
            final var missingKeys = new HashSet<>();
            while (matcher.find()) {
                final var key = matcher.group(1);
                final var values = params.get(key);
                if (values != null) {
                    matcher.appendReplacement(result, "");
                    for (int i = 0; i < values.length; i++) {
                        if (i > 0) result.append(',');
                        result.append(Matcher.quoteReplacement(toLog ? "[" + values[i] + "]" : "?"));
                        sqlValues.add(values[i]);
                    }
                } else {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                    missingKeys.add(key);
                }
            }
            if (!toLog && !missingKeys.isEmpty()) {
                throw new IllegalArgumentException("Missing value of the keys: " + missingKeys);
            }
            matcher.appendTail(result);
            return result.toString();
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
    }
}