package Engine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;


/**
 * The IndexingEngine manages in-memory data structures for fast retrieval of logs.
 * It provides various indexes by source, severity, category, and time.
 */
public class IndexingEngine {

    // using a static ConcurrentHashMap to store the log objects
    public static final Map<String, Set<LogObject>> HostIndex = new ConcurrentHashMap<>();
    public static final Map<String, Set<LogObject>> SeverityIndex = new ConcurrentHashMap<>();
    public static final Map<String, Set<LogObject>> CategoryIndex = new ConcurrentHashMap<>();

    // using a static ConcurrentSkipListMap to store the log objects by time
    public static final ConcurrentSkipListMap<java.time.LocalDate, ConcurrentSkipListMap<java.time.LocalTime, Set<LogObject>>> TimeIndex = new ConcurrentSkipListMap<>();

    /**
     * Adds a log object to all applicable in-memory indexes and optionally persists it to the database.
     *
     * @param logObject The log object to index.
     */
    public static void indexLog(LogObject logObject) {
        indexLog(logObject, true);
    }

    /**
     * Adds a log object to all applicable in-memory indexes.
     *
     * @param logObject The log object to index.
     * @param persist   True if the log should be saved to the database.
     */
    public static void indexLog(LogObject logObject, boolean persist) {
        try {
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(logObject.getTimestamp()),
                    java.time.ZoneId.systemDefault()
            );
            java.time.LocalDate day = dateTime.toLocalDate();
            java.time.LocalTime time = dateTime.toLocalTime().withSecond(0).withNano(0);

            // index by time
            TimeIndex.computeIfAbsent(day, k -> new ConcurrentSkipListMap<>())
                    .computeIfAbsent(time, k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                    .add(logObject);

            // index by host
            HostIndex.computeIfAbsent(logObject.getSource(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                    .add(logObject);

            // index by severity
            SeverityIndex.computeIfAbsent(logObject.getSeverity().toUpperCase(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                    .add(logObject);

            // index by category
            CategoryIndex.computeIfAbsent(logObject.getCategory().toUpperCase(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                    .add(logObject);

            if (persist) {
                DatabaseEngine.insertLog(logObject);
            }

        } catch (Exception e) {
            System.err.println("Error indexing log: " + e.getMessage());
        }
    }

    /**
     * Retrieves all logs for a given severity level.
     *
     * @param level The severity level (e.g., "CRIT", "WARN").
     * @return A list of log objects with the matching severity.
     */
    public static List<LogObject> getLogsBySeverity(String level) {
        if (level == null) return new ArrayList<>();
        return new ArrayList<>(SeverityIndex.getOrDefault(level.toUpperCase(), Collections.emptySet()));
    }

    /**
     * Retrieves all logs for a given category.
     *
     * @param category The category name.
     * @return A list of log objects in that category.
     */
    public static List<LogObject> getLogsByCategory(String category) {
        if (category == null) return new ArrayList<>();
        return new ArrayList<>(CategoryIndex.getOrDefault(category.toUpperCase(), Collections.emptySet()));
    }

    /**
     * Retrieves all logs for a specific day.
     *
     * @param day The date to retrieve logs for.
     * @return A list of log objects for that day.
     */
    public static List<LogObject> getLogsByDay(java.time.LocalDate day) {
        List<LogObject> results = new ArrayList<>();
        ConcurrentSkipListMap<java.time.LocalTime, Set<LogObject>> byTime = TimeIndex.get(day);
        if (byTime != null) {
            for (Set<LogObject> logs : byTime.values()) {
                results.addAll(logs);
            }
        }
        return results;
    }

    /**
     * Retrieves logs for a specific day within a given time range.
     *
     * @param day   The date.
     * @param start The start time.
     * @param end   The end time.
     * @return A list of log objects in the specified range.
     */
    public static List<LogObject> getLogsByDayAndTime(java.time.LocalDate day,
                                                      java.time.LocalTime start,
                                                      java.time.LocalTime end) {
        List<LogObject> results = new ArrayList<>();
        ConcurrentSkipListMap<java.time.LocalTime, Set<LogObject>> byTime = TimeIndex.get(day);
        if (byTime != null) {
            for (Set<LogObject> logs : byTime.subMap(start, true, end, true).values()) {
                results.addAll(logs);
            }
        }
        return results;
    }

    /**
     * Load previously persisted logs from SQLite into the in-memory indexes.
     * Called once at startup, before tailing begins.
     * Defaulting to 1 year of logs to ensure older records are available for browsing.
     */
    public static void loadFromDatabase(java.util.function.BiConsumer<Integer, Integer> progressCallback) {
        // Reduced from 1 year to 14 days to save memory on startup.
        // The user can still tail logs or we could add a way to load more on demand.
        DatabaseEngine.loadRecentLogs(24 * 14, log -> indexLog(log, false), progressCallback);
    }

    /**
     * Returns a set of all days for which logs are indexed.
     *
     * @return A set of LocalDates.
     */
    public static Set<java.time.LocalDate> getAvailableDays() {
        return TimeIndex.keySet();
    }

    /**
     * Returns a set of all times (rounded to minutes) for which logs exist on a specific day.
     *
     * @param day The date to check.
     * @return A set of LocalTimes.
     */
    public static Set<java.time.LocalTime> getAvailableTimes(java.time.LocalDate day) {
        ConcurrentSkipListMap<java.time.LocalTime, Set<LogObject>> byTime = TimeIndex.get(day);
        return byTime != null ? byTime.keySet() : Collections.emptySet();
    }

    /**
     * Retrieves all logs for a specific host.
     *
     * @param host The hostname or source.
     * @return A list of log objects from that host.
     */
    public static List<LogObject> getLogsForHost(String host) {
        if (host == null) return new ArrayList<>();
        return new ArrayList<>(HostIndex.getOrDefault(host, Collections.emptySet()));
    }

    /**
     * Returns a set of all unique hosts/sources found in the indexed logs.
     *
     * @return A set of hostnames.
     */
    public static Set<String> getHostKeys() {
        return HostIndex.keySet();
    }
}