import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class IndexingEngine{

    // using a static HashMap to store the log objects
    static final HashMap<String, List<LogObject>> HostIndex = new HashMap<>();
    // using a static TreeMap to store the log objects by time
    static final TreeMap<java.time.LocalDate, TreeMap<java.time.LocalTime, List<LogObject>>> TimeIndex = new TreeMap<>();

    // tail the log file in a separate thread
    static void tailFile(Path file) {
        try {
            // make sure the file exists before we start reading
            while (!Files.exists(file)) {
                Thread.sleep(500);
            }

            // start reading from the end of the file
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                long position = raf.length();
                raf.seek(position);
                // read the file line by line
                while (true) {
                    String line = raf.readLine();
                    // make sure we don't use a null line
                    if (line == null) {
                        Thread.sleep(100);
                        continue;
                    }
                    // calling the parser to parse the log line
                    SyslogParser parser = new SyslogParser();
                    parser.parse(line);
                }
            }
        } catch (Exception e) { // catch any exceptions that might occur
            System.err.println("Log tail stopped: " + e.getMessage());
        }
    }

    // helper to get logs by severity
    public static List<LogObject> getLogsBySeverity(String level) {
        List<LogObject> filtered = new ArrayList<>();
        for (TreeMap<java.time.LocalTime, List<LogObject>> byTime : TimeIndex.values()) {
            for (List<LogObject> logList : byTime.values()) {
                for (LogObject log : logList) {
                    if (log.getSeverity().equalsIgnoreCase(level)) {
                        filtered.add(log);
                    }
                }
            }
        }
        return filtered;
    }

    // helper to get logs by category
    public static List<LogObject> getLogsByCategory(String category) {
        List<LogObject> categorizedLogs = new ArrayList<>();
        for (TreeMap<java.time.LocalTime, List<LogObject>> byTime : TimeIndex.values()) {
            for (List<LogObject> logList : byTime.values()) {
                for (LogObject log : logList) {
                    if (log.getCategory().equalsIgnoreCase(category)) {
                        categorizedLogs.add(log);
                    }
                }
            }
        }
        return categorizedLogs;
    }

    // helper to get logs by day
    public static List<LogObject> getLogsByDay(java.time.LocalDate day) {
        List<LogObject> results = new ArrayList<>();
        TreeMap<java.time.LocalTime, List<LogObject>> byTime = TimeIndex.get(day);
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
        TreeMap<java.time.LocalTime, List<LogObject>> byTime = TimeIndex.get(day);
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
        TreeMap<java.time.LocalTime, List<LogObject>> byTime = TimeIndex.get(day);
        return byTime != null ? byTime.keySet() : Collections.emptySet();
    }

    // helper to get logs for a given host
    public static List<LogObject> getLogsForHost(String host) {
        return HostIndex.getOrDefault(host, new ArrayList<>());
    }

    // helper to get available host keys
    public static Set<String> getHostKeys() {
        return HostIndex.keySet();
    }
}