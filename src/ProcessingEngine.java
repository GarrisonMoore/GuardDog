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

    private static HashMap<String, List<LogObject>> HostIndex = new HashMap<>();
    private static TreeMap<Long, List<LogObject>> TimeIndex = new TreeMap<>();

    private static final Pattern LOG_PATTERN = Pattern.compile("^(\\S+\\s+\\d+\\s+\\d+:\\d+:\\d+)\\s+(\\S+)\\s+(\\S+):\\s+(.*)$");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.ENGLISH);

    static Scanner scanner = new Scanner(System.in);

    // dude
    public static void main(String[] args) throws IOException {
            // Check if we are receiving a pipe or reading a test file
            BufferedReader reader;
            if (System.in.available() > 0 || args.length == 0) {
                // This allows the 'pipe' from the terminal to work
                reader = new BufferedReader(new InputStreamReader(System.in));
                System.out.println("Watch Dog Engine: Listening to Live Stream...");
            } else {
                reader = new BufferedReader(new FileReader("src/test_logs.txt"));
                System.out.println("Watch Dog Engine: Reading Test File...");
            }

            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseAndProcess(line);
                    // Optional: System.out.println("Stream Catch: " + line);
                }
            } catch (Exception e) {
                System.err.println("Stream interrupted: " + e.getMessage());
            }

        // ... after the while loop finishes ...
        // ... after the while loop finishes ...
        System.out.println("Starting GUI...");

        // This single line applies the FlatLaf dark theme!
        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> {
            GUI myGui = new GUI();
            // Pass the keys (Hostnames) from your HashMap into the GUI list
            myGui.setHosts(HostIndex.keySet());
        });

//        System.out.println("Watch Dog Terminal Active. Type 'stats', 'search <host>', or 'exit'");
//        while (true) {
//            String input = scanner.nextLine();
//            if (input.equalsIgnoreCase("exit")) break;
//
//            if (input.equalsIgnoreCase("stats")) {
//                displayStats();
//            } else if (input.startsWith("search ")) {
//                String query = input.substring(7); // Grabs everything after 'search '
//                List<LogObject> results = HostIndex.get(query);
//                if (results != null) {
//                    results.forEach(System.out::println);
//                } else {
//                    System.out.println("No records for " + query);
//                }
//            }
//        }
    }

    private static void parseAndProcess(String rawLine) {
        Matcher matcher = LOG_PATTERN.matcher(rawLine);
        if (matcher.find()) {
            try {
                String timeStr = matcher.group(1).trim();
                // Use a temporary object to hold the Month/Day/Time
                DateTimeFormatter tempFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.ENGLISH);

                // We have to parse it as a TemporalAccessor first because it lacks a year
                java.time.temporal.TemporalAccessor accessor = tempFormatter.parse(timeStr);
                int month = accessor.get(java.time.temporal.ChronoField.MONTH_OF_YEAR);
                int day = accessor.get(java.time.temporal.ChronoField.DAY_OF_MONTH);
                int hour = accessor.get(java.time.temporal.ChronoField.HOUR_OF_DAY);
                int minute = accessor.get(java.time.temporal.ChronoField.MINUTE_OF_HOUR);
                int second = accessor.get(java.time.temporal.ChronoField.SECOND_OF_MINUTE);

                // Now build the full LocalDateTime with the current year
                LocalDateTime ldt = LocalDateTime.of(LocalDateTime.now().getYear(), month, day, hour, minute, second);
                long epochTime = ldt.atZone(ZoneId.systemDefault()).toEpochSecond();

                String host = matcher.group(2);
                String msg = matcher.group(4);

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
                System.out.println("Indexed log from: " + host + " [" + epochTime + "]");

            } catch (Exception e) {
                // Print the error so you can see WHY it's skipping
                System.err.println("Skipping malformed log: " + rawLine + " | Error: " + e.getMessage());
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
