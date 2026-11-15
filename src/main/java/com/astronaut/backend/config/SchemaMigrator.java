package com.astronaut.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Agrega columnas nuevas a tablas que ya existen (funciona en MySQL y en H2).
 * Hibernate (ddl-auto=update) normalmente lo hace, pero esto lo garantiza para
 * las bases creadas con versiones anteriores de la app.
 */
@Configuration
@DependsOn("entityManagerFactory") // después de que Hibernate cree/actualice las tablas
public class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    private final DataSource dataSource;

    public SchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    void migrate() {
        ensureColumn("app_settings", "report_retention_days", "INTEGER");
        ensureColumn("astronauts", "body_battery", "INTEGER");
        ensureColumn("astronauts", "hrv", "DOUBLE");
        // niveles de alerta: antes solo VERY_LOW / VERY_HIGH (ENUM nativo en MySQL); ahora también LOW / HIGH
        ensureVarchar("notifications", "alert_level", 20);
        ensureVarchar("notification_alert_states", "alert_level", 20);
        ensureColumn("notifications", "severity", "VARCHAR(20)");
    }

    /** Convierte una columna ENUM nativa en VARCHAR para aceptar valores nuevos. */
    void ensureVarchar(String table, String column, int length) {
        try (Connection c = dataSource.getConnection()) {
            if (!tableExists(c, table)) {
                return;
            }
            String type = columnType(c, table, column);
            if (type == null || !type.toUpperCase().contains("ENUM")) {
                return;
            }
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            String sql = product.contains("mysql") || product.contains("mariadb")
                    ? "ALTER TABLE " + table + " MODIFY " + column + " VARCHAR(" + length + ")"
                    : "ALTER TABLE " + table + " ALTER COLUMN " + column + " VARCHAR(" + length + ")";
            try (Statement st = c.createStatement()) {
                st.executeUpdate(sql);
                log.info("Columna {}.{} convertida de {} a VARCHAR({})", table, column, type, length);
            }
        } catch (SQLException e) {
            log.warn("No se pudo convertir la columna {}.{} a VARCHAR: {}", table, column, e.getMessage());
        }
    }

    private static String columnType(Connection c, String table, String column) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        for (String t : new String[]{table, table.toUpperCase()}) {
            for (String col : new String[]{column, column.toUpperCase()}) {
                try (ResultSet rs = md.getColumns(c.getCatalog(), null, t, col)) {
                    if (rs.next()) {
                        return rs.getString("TYPE_NAME");
                    }
                }
            }
        }
        return null;
    }

    void ensureColumn(String table, String column, String sqlType) {
        try (Connection c = dataSource.getConnection()) {
            if (!tableExists(c, table) || columnExists(c, table, column)) {
                return;
            }
            try (Statement st = c.createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + sqlType);
                log.info("Columna agregada: {}.{}", table, column);
            }
        } catch (SQLException e) {
            log.warn("No se pudo verificar/agregar la columna {}.{}: {}", table, column, e.getMessage());
        }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        for (String name : new String[]{table, table.toUpperCase()}) {
            try (ResultSet rs = md.getTables(c.getCatalog(), null, name, null)) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean columnExists(Connection c, String table, String column) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        for (String t : new String[]{table, table.toUpperCase()}) {
            for (String col : new String[]{column, column.toUpperCase()}) {
                try (ResultSet rs = md.getColumns(c.getCatalog(), null, t, col)) {
                    if (rs.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
