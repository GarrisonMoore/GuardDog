import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.*;


public class IndexingEngine{

    static GUI myGui;
    static final HashMap<String, List<LogObject>> HostIndex = new HashMap<>();
    static final TreeMap<java.time.LocalDate, TreeMap<java.time.LocalTime, List<LogObject>>> TimeIndex = new TreeMap<>();
    static void tailFile(Path file) {
        try {
            while (!Files.exists(file)) {
                Thread.sleep(500);
            }

            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                long position = raf.length();
                raf.seek(position);

                while (true) {
                    String line = raf.readLine();

                    if (line == null) {
                        Thread.sleep(100);
                        continue;
                    }
                    // java bro
                    SyslogParser parser = new SyslogParser();
                    parser.parse(line);
                }
            }
        } catch (Exception e) {
            System.err.println("Log tail stopped: " + e.getMessage());
        }
    }

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

    public static List<LogObject> getLogsForHost(String host) {
        return HostIndex.getOrDefault(host, new ArrayList<>());
    }

    public static Set<String> getHostKeys() {
        return HostIndex.keySet();
    }
}