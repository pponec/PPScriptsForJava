/*
 * Common utilities for Java17+ for the CLI (command line interface).
 * Usage: java -cp ../lib/h2-2.2.224.jar SqlExecutor.java
 *
 * Environment: Java 17+ with JDBC driver com.h2database:h2:2.2.224 are required.
 * Licence: Apache License, Version 2.0, Pavel Ponec, https://github.com/pponec/DirectoryBookmarks
 */
package utils;

import java.io.PrintStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.jdbi.v3.core.Jdbi;

/**
 * Use SQL statements by the SqlParamBuilder class.
 *
 * https://www.baeldung.com/jdbi
 *
 */
public final class SqlExecutorJdbi {

    private final static ConnectionProvider db = ConnectionProvider.forH2("user", "pwd");
    private final PrintStream out = System.out;

    public static void main(final String[] args) throws Exception {
        new SqlExecutorJdbi().mainStart(args);
    }

    void mainStart(String... args) throws Exception {
        // Inicializace JDBI instance s použitím JDBC URL pro připojení k databázi
        Jdbi jdbi = Jdbi.open(db.connection()).getJdbi();

        // CREATE TABLE
        List<Employee> employees = jdbi.withHandle(handle -> {
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS employee
                    ( id INTEGER PRIMARY KEY
                    , name VARCHAR(256) DEFAULT 'test'
                    , code VARCHAR(1)
                    , created DATE NOT NULL)
                    """.stripIndent());

            handle.createUpdate("""
                            INSERT INTO employee
                            ( id, code, created) VALUES
                            ( :id1, :code, :created),
                            ( :id2, :code, :created)
                            """.stripIndent())
                    .bind("id1", 1)
                    .bind("id2", 2)
                    .bind("code", "T")
                    .bind("created", LocalDate.parse("2024-04-14"))
                    .execute();

            handle.createUpdate("""
                            INSERT INTO employee
                            ( id, code, created) VALUES
                            ( :id1, :code, :created),
                            ( :id2, :code, :created)
                            """.stripIndent())
                    .bind("id1", 11)
                    .bind("id2", 12)
                    .bind("code", "V")
                    .bind("created", LocalDate.parse("2024-04-14"))
                    .execute();

            return handle.createQuery("""
                            SELECT t.id, t.code, t.created
                            FROM employee t
                            WHERE t.id < :id
                              AND t.code IN (:code)
                            ORDER BY t.id
                            """)
                    .bind("id", 100)
                    //.bindList("code", Arrays.asList("T", "V"))
                    .bind("code", "T")
                    .mapToBean(Employee.class)
                    .list();
        });

        // Výpis výsledků SELECT operace
        for (Employee employee : employees) {
            System.out.println("ID: " + employee.getId()
                    + ", Code: " + employee.getCode()
                    + ", Created: " + employee.getCreated());
        }

    }


    // Třída reprezentující odpovídající strukturu databázové tabulky
    public static class Employee {
        private int id;
        private String code;
        private LocalDate created;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public LocalDate getCreated() {
            return created;
        }

        public void setCreated(LocalDate created) {
            this.created = created;
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
