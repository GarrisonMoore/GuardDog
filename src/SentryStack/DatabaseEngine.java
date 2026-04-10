package SentryStack;

import java.sql.*;
import java.util.List;

/**
 * SQLite persistence layer for Guard Dog logs.
 * Stores processed LogObjects so data survives restarts.
 */
public class DatabaseEngine {

    private static final String DB_URL = "jdbc:sqlite:guarddog_logs.db";
    private static Connection connection;

    /**
     * Initialize the database and create the logs table if it doesn't exist.
     */
    public static void initialize() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            connection.setAutoCommit(false); // We'll batch-commit for performance

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS logs (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp INTEGER NOT NULL,
                        source    TEXT    NOT NULL,
                        severity  TEXT    NOT NULL,
                        category  TEXT    NOT NULL,
                        pid       TEXT,
                        message   TEXT    NOT NULL
                    )
                """);

                // Indexes for the queries your GUI already does
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_source   ON logs(source)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_severity ON logs(severity)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_category ON logs(category)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_time     ON logs(timestamp)");
            }
            connection.commit();
            System.out.println("SQLite database initialized: " + DB_URL);
        } catch (SQLException e) {
            System.err.println("Failed to initialize SQLite: " + e.getMessage());
        }
    }

    /**
     * Insert a single LogObject into the database.
     */
    public static synchronized void insertLog(LogObject log) {
        if (connection == null) return;

        String sql = "INSERT INTO logs (timestamp, source, severity, category, pid, message) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, log.getTimestamp());
            ps.setString(2, log.getSource());
            ps.setString(3, log.getSeverity());
            ps.setString(4, log.getCategory());
            ps.setString(5, log.getPid());
            ps.setString(6, log.getMessage());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to insert log: " + e.getMessage());
        }
    }

    /**
     * Call periodically (or after a batch of inserts) to flush to disk.
     */
    public static synchronized void commit() {
        if (connection == null) return;
        try {
            connection.commit();
        } catch (SQLException e) {
            System.err.println("Failed to commit: " + e.getMessage());
        }
    }

    /**
     * Clean shutdown.
     */
    public static void close() {
        if (connection == null) return;
        try {
            connection.commit();
            connection.close();
            System.out.println("SQLite connection closed.");
        } catch (SQLException e) {
            System.err.println("Error closing SQLite: " + e.getMessage());
        }
    }

    /**
     * Load recent logs from the database (last N hours) using a stream.
     */
    public static void loadRecentLogs(int hoursBack, java.util.function.Consumer<LogObject> processor) {
        if (connection == null) return;

        long cutoff = java.time.Instant.now().minus(hoursBack, java.time.temporal.ChronoUnit.HOURS).getEpochSecond();

        String sql = "SELECT timestamp, source, severity, category, pid, message FROM logs WHERE timestamp >= ? ORDER BY timestamp";
        int count = 0;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, cutoff);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LogObject log = new LogObject(
                            rs.getLong("timestamp"),
                            rs.getString("source"),
                            rs.getString("severity"),
                            rs.getString("category"),
                            rs.getString("pid"),
                            rs.getString("message")
                    );
                    // Pass the log directly to the IndexingEngine one at a time
                    processor.accept(log);
                    count++;
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load logs from SQLite: " + e.getMessage());
        }
        System.out.println("Loaded " + count + " recent logs from database (last " + hoursBack + "h).");
    }
}