import java.util.ArrayList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyslogParser implements LogParser {

    // RFC-5424 format Regex Tokenizer
    private static final Pattern RFC5424_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[^\\s]*)\\s+(\\S+)\\s+(?:.*:\\s+)?(.*)$");

    // BSD syslog format Regex Tokenizer
    private static final Pattern BSD_PATTERN = Pattern.compile("^([A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(\\S+)\\s+(.*)$");

    private static final java.time.format.DateTimeFormatter BSD_FORMATTER = new java.time.format.DateTimeFormatterBuilder()
            .appendPattern("MMM d HH:mm:ss")
            .parseDefaulting(java.time.temporal.ChronoField.YEAR, java.time.LocalDate.now().getYear())
            .toFormatter(java.util.Locale.ENGLISH);

    // override the canParse method in the LogParser interface (do this for any new parser)
    @Override
    public boolean canParse(String rawline) {
        // make sure the line matches the RFC-5424 format
        return rawline.matches("^\\d{4}-\\d{2}.*") || rawline.matches("^[A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}.*");
    }

    // override the parse method in the LogParser interface (do this for any new parser)
    // parse the line using the RFC-5424 and BSD format
    @Override
    public LogObject parse(String rawline) {
        Matcher m = RFC5424_PATTERN.matcher(rawline);
        long epochTime = 0;
        String host = "";
        String msg = "";

        if (m.find()) {
            try {
                epochTime = java.time.OffsetDateTime.parse(m.group(1)).toEpochSecond();
                host = m.group(2);
                msg = m.group(3);
            } catch (Exception e) {
                System.err.println("Error parsing RFC5424 log: " + e.getMessage());
                return null;
            }
        } else {
            m = BSD_PATTERN.matcher(rawline);
            if (m.find()) {
                try {
                    String timestampStr = m.group(1);
                    // Handle single digit day spacing if present (e.g., "Mar  1")
                    timestampStr = timestampStr.replaceAll("\\s+", " ");
                    epochTime = java.time.LocalDateTime.parse(timestampStr, BSD_FORMATTER)
                            .atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
                    host = m.group(2);
                    msg = m.group(3);
                } catch (Exception e) {
                    System.err.println("Error parsing BSD log: " + e.getMessage());
                    return null;
                }
            } else {
                return null;
            }
        }

        String lowerMsg = msg.toLowerCase();

        // throw away windows noise first
        if (lowerMsg.contains("the locale specific resource for the desired message is not present")) {
            msg = "computer fart noises";
            lowerMsg = msg.toLowerCase();
        }

        // --- CATEGORIZATION ---
        String severity = "INFO";
        String category = "UNCATEGORIZED";

        // Severities - Check more critical first
        if (lowerMsg.contains("fail") || lowerMsg.contains("error") || lowerMsg.contains("exception") || lowerMsg.contains("failed") || lowerMsg.contains("err")) {
            severity = "CRIT";
            category = "ERRORS";
        } else if (lowerMsg.contains("warn") || lowerMsg.contains("timeout") || lowerMsg.contains("warning") || lowerMsg.contains("blocked") || lowerMsg.contains("denied")) {
            severity = "WARN";
            category = "WARNINGS";
        }

        // Categories
        if (category.equals("UNCATEGORIZED")) {
            if (lowerMsg.contains("warn") || lowerMsg.contains("timeout") || lowerMsg.contains("warning")
                    || lowerMsg.contains("blocked") || lowerMsg.contains("denied")) {
                category = "WARNINGS";
            } else if (lowerMsg.contains("logon") || lowerMsg.contains("auth") || lowerMsg.contains("access")
                    || lowerMsg.contains("request") || lowerMsg.contains("login")) {
                category = "AUTH EVENTS";
            } else if (lowerMsg.contains("audit") || lowerMsg.contains("auditd")) {
                category = "AUDIT";
            } else if (lowerMsg.contains("group") || lowerMsg.contains("policy") || lowerMsg.contains(".local") ||
                    lowerMsg.contains("10.202.69.") || lowerMsg.contains("{") || lowerMsg.contains("kbps") ||
                    lowerMsg.contains("wallpaper") || lowerMsg.contains("wallpapers") || lowerMsg.contains("none")) {
                category = "GROUP POLICY";
            }else if (lowerMsg.contains("winrm") || lowerMsg.contains("powershell") || lowerMsg.contains("command") ||
                    lowerMsg.contains("task") || lowerMsg.contains("service") || lowerMsg.contains("server") ||
            lowerMsg.contains("remote") || lowerMsg.contains("management") || lowerMsg.contains("remote management")) {
                category = "REMOTE MANAGEMENT";
            }
        }

        // create a new log object for any logs that fit the above categories
        LogObject logObject = new LogObject(epochTime, host, severity, category, msg);

        try {
            // convert epoch time to local date and time
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(epochTime),
                    java.time.ZoneId.systemDefault()
            );
            java.time.LocalDate day = dateTime.toLocalDate();
            java.time.LocalTime time = dateTime.toLocalTime().withSecond(0).withNano(0);

            // compute time index
            IndexingEngine.TimeIndex
                    .computeIfAbsent(day, k -> new TreeMap<>())
                    .computeIfAbsent(time, k -> new ArrayList<>())
                    .add(logObject);

            // append to live log display pane
            if (GUI.getMyGui() != null) {
                GUI.getMyGui().appendLiveLog(logObject);
            }

            // compute host index
            IndexingEngine.HostIndex.computeIfAbsent(host, k -> new ArrayList<>()).add(logObject);

        } catch (Exception e) {
            // Don't ignore parsing errors bruh
            System.err.println("Error indexing log: " + e.getMessage());
        }
        return logObject;
    }
}