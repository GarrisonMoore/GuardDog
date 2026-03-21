import javax.swing.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.formdev.flatlaf.FlatDarkLaf;

public class ProcessingEngine {

    private static GUI myGui;
    private static final HashMap<String, List<LogObject>> HostIndex = new HashMap<>();
    private static final TreeMap<Long, List<LogObject>> TimeIndex = new TreeMap<>();

    // Windows ISO Log Pattern
    private static final Pattern LOG_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[^\\s]*)\\s+(\\S+)\\s+.*:\\s+(.*)$");

    private static final Path LOG_FILE = Paths.get("/var/log/windows_5141.log");

    public static void main(String[] args) {
        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> {
            myGui = new GUI();
            myGui.setHosts(HostIndex.keySet());
        });

        Thread logThread = new Thread(() -> watchLogFile(LOG_FILE), "log-watcher");
        logThread.setDaemon(true);
        logThread.start();
    }

    private static void watchLogFile(Path file) {
        long lastPosition = 0;

        try {
            if (Files.exists(file)) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        parseAndProcess(line);
                    }
                }
                lastPosition = Files.size(file);
            }

            while (true) {
                long currentSize = Files.exists(file) ? Files.size(file) : 0;

                if (currentSize < lastPosition) {
                    // File was truncated/rotated; start over
                    lastPosition = 0;
                }

                if (currentSize > lastPosition) {
                    try (FileReader fr = new FileReader(file.toFile())) {
                        long skipped = fr.skip(lastPosition);
                        while (skipped < lastPosition) {
                            long more = fr.skip(lastPosition - skipped);
                            if (more <= 0) break;
                            skipped += more;
                        }

                        try (BufferedReader reader = new BufferedReader(fr)) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                parseAndProcess(line);
                                scheduleGuiRefresh();
                            }
                        }
                    }
                    lastPosition = currentSize;
                }

                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.err.println("Log watcher stopped: " + e.getMessage());
        }
    }

    private static void scheduleGuiRefresh() {
        if (myGui != null) {
            SwingUtilities.invokeLater(() -> {
                myGui.setHosts(HostIndex.keySet());
                myGui.refreshDisplay();
            });
        }
    }

    private static void parseAndProcess(String rawLine) {
        Matcher matcher = LOG_PATTERN.matcher(rawLine);
        if (matcher.find()) {
            try {
                long epochTime = java.time.OffsetDateTime.parse(matcher.group(1)).toEpochSecond();
                String host = matcher.group(2);
                String msg = matcher.group(3);

                String severity = "INFO";
                String lowerMsg = msg.toLowerCase();
                if (lowerMsg.contains("fail") || lowerMsg.contains("error")) severity = "CRIT";
                if (lowerMsg.contains("warn") || lowerMsg.contains("timeout")) severity = "WARN";

                LogObject logObject = new LogObject(epochTime, host, severity, msg);
                TimeIndex.computeIfAbsent(epochTime, k -> new ArrayList<>()).add(logObject);
                HostIndex.computeIfAbsent(host, k -> new ArrayList<>()).add(logObject);
            } catch (Exception e) {
                // Ignore silent parse errors
            }
        }
    }

    public static List<LogObject> getLogsBySeverity(String level) {
        List<LogObject> filtered = new ArrayList<>();
        for (List<LogObject> logList : TimeIndex.values()) {
            for (LogObject log : logList) {
                if (log.getLevel().equalsIgnoreCase(level)) filtered.add(log);
            }
        }
        return filtered;
    }

    public static List<LogObject> getLogsByTime(int minutes) {
        long cutoff = (System.currentTimeMillis() / 1000) - (minutes * 60L);
        List<LogObject> results = new ArrayList<>();
        for (List<LogObject> logList : TimeIndex.tailMap(cutoff).values()) results.addAll(logList);
        return results;
    }

    public static List<LogObject> getLogsForHost(String host) {
        return HostIndex.getOrDefault(host, new ArrayList<>());
    }

    public static Set<String> getHostKeys() {
        return HostIndex.keySet();
    }
}