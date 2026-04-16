package SentryStack;

import GUI.GUI;
import Interfaces.ParserMaster;
import Parsers.BSD.BSDparser;
import Parsers.Heuristic.HeuristicParser;
import Parsers.JSON.JSONParser;
import Parsers.Syslog.SyslogParser;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;


public class IndexingEngine {

    // using a static ConcurrentHashMap to store the log objects
    public static final Map<String, Set<LogObject>> HostIndex = new ConcurrentHashMap<>();
    public static final Map<String, Set<LogObject>> SeverityIndex = new ConcurrentHashMap<>();
    public static final Map<String, Set<LogObject>> CategoryIndex = new ConcurrentHashMap<>();

    // using a static ConcurrentSkipListMap to store the log objects by time
    public static final ConcurrentSkipListMap<java.time.LocalDate, ConcurrentSkipListMap<java.time.LocalTime, Set<LogObject>>> TimeIndex = new ConcurrentSkipListMap<>();

    // List of parsers to use
    private static final List<ParserMaster> parsers = new ArrayList<>();

    static {
        parsers.add(new SyslogParser());
        parsers.add(new JSONParser());
        parsers.add(new BSDparser());
        // Use HeuristicParser as the last parser to catch any remaining logs
        parsers.add(new HeuristicParser());
        // Add other parsers here as needed
    }

    // tail the log file in a separate thread
    static void tailFile(Path file) {
        try {
            // make sure the file exists before we start reading
            while (!Files.exists(file)) {
                Thread.sleep(500);
            }

            // start reading from the end of the file by default to prevent memory exhaustion on large logs
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                long fileLength = raf.length();
                // Seek to the end of the file. 
                // To support a small amount of history (e.g., last 10KB), we could seek to fileLength - 10240
                raf.seek(fileLength);

                // read the file line by line as new content is appended
                while (true) {
                    String line = raf.readLine();
                    // make sure we don't use a null line
                    if (line == null) {
                        if (raf.length() < raf.getFilePointer()) {
                            // File was truncated
                            raf.seek(0);
                        } else {
                            Thread.sleep(250);
                        }
                        continue;
                    }

                    // Decode ISO-8859-1 to UTF-8 (readLine uses ISO-8859-1)
                    line = new String(line.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), java.nio.charset.StandardCharsets.UTF_8);

                    // Try each parser
                    for (ParserMaster parser : parsers) {
                        if (parser.canParse(line)) {
                            LogObject log = parser.parse(line);
                            if (log != null) {
                                indexLog(log);
                                // update GUI.GUI
                                if (GUI.getMyGui() != null) {
                                    GUI.getMyGui().appendLiveLog(log);
                                }
                                break; // Found a parser, move to next line
                            }
                        }
                    }
                }
            }
        } catch (Exception e) { // catch any exceptions that might occur
            System.err.println("Log tail stopped: " + e.getMessage());
        }
    }

    private static void indexLog(LogObject logObject) {
        indexLog(logObject, true);
    }

    private static void indexLog(LogObject logObject, boolean persist) {
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

    // helper to get logs by severity
    public static List<LogObject> getLogsBySeverity(String level) {
        if (level == null) return new ArrayList<>();
        return new ArrayList<>(SeverityIndex.getOrDefault(level.toUpperCase(), Collections.emptySet()));
    }

    // helper to get logs by category
    public static List<LogObject> getLogsByCategory(String category) {
        if (category == null) return new ArrayList<>();
        return new ArrayList<>(CategoryIndex.getOrDefault(category.toUpperCase(), Collections.emptySet()));
    }

    // helper to get logs by day
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

    // helper to get logs by day and time
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
     */
    public static void loadFromDatabase() {
        DatabaseEngine.loadRecentLogs(24 * 3, log -> indexLog(log, false));
    }

    // helper to show available days
    public static Set<java.time.LocalDate> getAvailableDays() {
        return TimeIndex.keySet();
    }

    // helper to show available times for a given day
    public static Set<java.time.LocalTime> getAvailableTimes(java.time.LocalDate day) {
        ConcurrentSkipListMap<java.time.LocalTime, Set<LogObject>> byTime = TimeIndex.get(day);
        return byTime != null ? byTime.keySet() : Collections.emptySet();
    }

    // helper to get logs for a given host
    public static List<LogObject> getLogsForHost(String host) {
        if (host == null) return new ArrayList<>();
        return new ArrayList<>(HostIndex.getOrDefault(host, Collections.emptySet()));
    }

    // helper to get available host keys
    public static Set<String> getHostKeys() {
        return HostIndex.keySet();
    }
}