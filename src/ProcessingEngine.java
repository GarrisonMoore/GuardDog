import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.formdev.flatlaf.FlatDarkLaf;

public class ProcessingEngine {

    private static GUI myGui;
    private static HashMap<String, List<LogObject>> HostIndex = new HashMap<>();
    private static TreeMap<Long, List<LogObject>> TimeIndex = new TreeMap<>();

    // Windows ISO Log Pattern
    private static final Pattern LOG_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[^\\s]*)\\s+(\\S+)\\s+.*:\\s+(.*)$");

    public static void main(String[] args) {
        FlatDarkLaf.setup();

        // 1. LAUNCH GUI FIRST
        SwingUtilities.invokeLater(() -> {
            myGui = new GUI();
            myGui.setHosts(HostIndex.keySet());
        });

        // 2. BACKGROUND THREAD FOR THE PIPE (Stops the freezing!)
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseAndProcess(line);

                    // 3. MUST update the GUI safely on the Event Dispatch Thread
                    if (myGui != null) {
                        SwingUtilities.invokeLater(() -> myGui.setHosts(HostIndex.keySet()));
                    }
                }
            } catch (Exception e) {
                System.err.println("Pipe closed.");
            }
        });

        logThread.setDaemon(true);
        logThread.start();
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

    // --- KEEP YOUR EXISTING GETTER METHODS BELOW ---
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