import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;


public class IndexingEngine{

    // using a static ConcurrentHashMap to store the log objects
    static final ConcurrentHashMap<String, List<LogObject>> HostIndex = new ConcurrentHashMap<>();
    // using a static ConcurrentSkipListMap to store the log objects by time
    static final ConcurrentSkipListMap<java.time.LocalDate, ConcurrentSkipListMap<java.time.LocalTime, List<LogObject>>> TimeIndex = new ConcurrentSkipListMap<>();

    // List of parsers to use
    private static final List<LogParser> parsers = new ArrayList<>();

    static {
        parsers.add(new SyslogParser());
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
                        Thread.sleep(100);
                        continue;
                    }

                    // Try each parser
                    for (LogParser parser : parsers) {
                        if (parser.canParse(line)) {
                            LogObject log = parser.parse(line);
                            if (log != null) {
                                indexLog(log);
                                // update GUI
                                if (GUI.getMyGui() != null) {
                                    GUI.getMyGui().appendLiveLog(log);
                                }
                                break; // Found a parser, move to next line
                            }
                        }
                    }
                    // Throttle to keep app responsive
                    Thread.sleep(1);
                }
            }
        } catch (Exception e) { // catch any exceptions that might occur
            System.err.println("Log tail stopped: " + e.getMessage());
        }
    }

    private static void indexLog(LogObject logObject) {
        try {
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(logObject.getTimestamp()),
                    java.time.ZoneId.systemDefault()
            );
            java.time.LocalDate day = dateTime.toLocalDate();
            java.time.LocalTime time = dateTime.toLocalTime().withSecond(0).withNano(0);

            // index by time
            TimeIndex.computeIfAbsent(day, k -> new ConcurrentSkipListMap<>())
                     .computeIfAbsent(time, k -> new CopyOnWriteArrayList<>())
                     .add(logObject);

            // index by host
            HostIndex.computeIfAbsent(logObject.getSource(), k -> new CopyOnWriteArrayList<>())
                     .add(logObject);
        } catch (Exception e) {
            System.err.println("Error indexing log: " + e.getMessage());
        }
    }

    // helper to get logs by severity
    public static List<LogObject> getLogsBySeverity(String level) {
        List<LogObject> filtered = new ArrayList<>();
        for (ConcurrentSkipListMap<java.time.LocalTime, List<LogObject>> byTime : TimeIndex.values()) {
            for (List<LogObject> logList : byTime.values()) {
                synchronized (logList) {
                    for (LogObject log : logList) {
                        if (log.getSeverity().equalsIgnoreCase(level)) {
                            filtered.add(log);
                        }
                    }
                }
            }
        }
        return filtered;
    }

    // helper to get logs by category
    public static List<LogObject> getLogsByCategory(String category) {
        List<LogObject> categorizedLogs = new ArrayList<>();
        for (ConcurrentSkipListMap<java.time.LocalTime, List<LogObject>> byTime : TimeIndex.values()) {
            for (List<LogObject> logList : byTime.values()) {
                synchronized (logList) {
                    for (LogObject log : logList) {
                        if (log.getCategory().equalsIgnoreCase(category)) {
                            categorizedLogs.add(log);
                        }
                    }
                }
            }
        }
        return categorizedLogs;
    }

    // helper to get logs by day
    public static List<LogObject> getLogsByDay(java.time.LocalDate day) {
        List<LogObject> results = new ArrayList<>();
        ConcurrentSkipListMap<java.time.LocalTime, List<LogObject>> byTime = TimeIndex.get(day);
        if (byTime != null) {
            for (List<LogObject> logs : byTime.values()) {
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
        ConcurrentSkipListMap<java.time.LocalTime, List<LogObject>> byTime = TimeIndex.get(day);
        if (byTime != null) {
            for (List<LogObject> logs : byTime.subMap(start, true, end, true).values()) {
                results.addAll(logs);
            }
        }
        return results;
    }

    // helper to show available days
    public static Set<java.time.LocalDate> getAvailableDays() {
        return TimeIndex.keySet();
    }

    // helper to show available times for a given day
    public static Set<java.time.LocalTime> getAvailableTimes(java.time.LocalDate day) {
        ConcurrentSkipListMap<java.time.LocalTime, List<LogObject>> byTime = TimeIndex.get(day);
        return byTime != null ? byTime.keySet() : Collections.emptySet();
    }

    // helper to get logs for a given host
    public static List<LogObject> getLogsForHost(String host) {
        return HostIndex.getOrDefault(host, Collections.emptyList());
    }

    // helper to get available host keys
    public static Set<String> getHostKeys() {
        return HostIndex.keySet();
    }
}