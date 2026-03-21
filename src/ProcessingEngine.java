import javax.swing.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.ZoneId;

import com.formdev.flatlaf.FlatDarkLaf;

public class ProcessingEngine {

    private static GUI myGui;
    private static HashMap<String, List<LogObject>> HostIndex = new HashMap<>();
    private static TreeMap<Long, List<LogObject>> TimeIndex = new TreeMap<>();

    private static final Pattern LOG_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[^\\s]*)\\s+(\\S+)\\s+.*:\\s+(.*)$");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.ENGLISH);

    /// dude
    public static void main(String[] args) throws IOException {
        FlatDarkLaf.setup();

        // 1. LAUNCH GUI FIRST - This is the "Thread" fix
        SwingUtilities.invokeLater(() -> {
            myGui = new GUI();
            myGui.setHosts(HostIndex.keySet());
        });

        // 2. READ THE PIPE
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseAndProcess(line);

                // 3. LIVE REFRESH - Update the UI as logs fly in
                if (myGui != null) {
                    myGui.setHosts(HostIndex.keySet());
                }
            }
        } catch (Exception e) {
            // Silently handle pipe close on exit
        }
    }

    private static void parseAndProcess(String rawLine) {
        Matcher matcher = LOG_PATTERN.matcher(rawLine);
        if (matcher.find()) {
            try {
                // ISO 8601 is much easier to parse!
                String timeStr = matcher.group(1);
                long epochTime = java.time.OffsetDateTime.parse(timeStr).toEpochSecond();

                String host = matcher.group(2);
                String msg = matcher.group(3);

                // --- NEW SEVERITY SCANNER ---
                String severity = "INFO";
                String lowerMsg = msg.toLowerCase();

                if (lowerMsg.contains("failed") || lowerMsg.contains("error") || lowerMsg.contains("critical") || lowerMsg.contains("offline")) {
                    severity = "CRIT";
                } else if (lowerMsg.contains("warning") || lowerMsg.contains("timeout") || lowerMsg.contains("high temp")) {
                    severity = "WARN";
                }

                // Pass the new dynamic severity into your object
                LogObject logObject = new LogObject(epochTime, host, severity, msg);

                // ... indexing logic ...
                TimeIndex.computeIfAbsent(epochTime, k -> new ArrayList<>()).add(logObject);
                HostIndex.computeIfAbsent(host, k -> new ArrayList<>()).add(logObject);

            } catch (Exception e) {
                // Log it to terminal so you can see if the date parser is still mad
                System.err.println("Parse Error: " + e.getMessage());
            }
        }
    }

    // Filter logs by severity
    public static List<LogObject> getLogsBySeverity(String level) {
        List<LogObject> filtered = new ArrayList<>();
        // Outer loop: iterate through each List in the TreeMap
        for (List<LogObject> logList : TimeIndex.values()) {
            // Inner loop: iterate through each log in that specific second
            for (LogObject log : logList) {
                if (log.getLevel().equalsIgnoreCase(level)) {
                    filtered.add(log);
                }
            }
        }
        return filtered;
    }

    // Filter logs by time using your TreeMap's O(log n) efficiency
    public static List<LogObject> getLogsByTime(int minutes) {
        long nowInSeconds = System.currentTimeMillis() / 1000;
        long cutoff = nowInSeconds - (minutes * 60L);

        List<LogObject> results = new ArrayList<>();
        // Get the part of the map newer than the cutoff
        for (List<LogObject> logList : TimeIndex.tailMap(cutoff).values()) {
            results.addAll(logList); // Add all logs from this second to our results
        }
        return results;
    }

    // Getter for the GUI
    public static List<LogObject> getLogsForHost(String host) {
        return HostIndex.getOrDefault(host, new ArrayList<>());
    }

    public static Set<String> getHostKeys() {
        return HostIndex.keySet();
    }

    // Dumps Current Processed data
    public static void displayStats() {
        System.out.println("\n--- Watch Dog Statistics ---");
        System.out.println("Total Unique Hosts: " + HostIndex.size());
        System.out.println("Total Logs in Memory: " + TimeIndex.size());

        HostIndex.forEach((host, logs) -> {
            System.out.println(" > " + host + ": " + logs.size() + " entries");
        });
    }
}
