package Parsers.Syslog;

import Interfaces.CategorizationMaster;
import SentryStack.LogObject;
import Interfaces.ParserMaster;
import Interfaces.ParseStatus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyslogParser implements ParserMaster {

    // Matches true RFC-5424 format:
    // Optional <PRI>VERSION, then TIMESTAMP, HOST, APP, PID, MSGID, and the rest (Structured Data + MSG)
    private static final Pattern RFC5424_PATTERN = Pattern.compile(
            "^(?:<\\d+>\\d+\\s+)?(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(.*)$"
    );

    @Override
    public boolean canParse(String rawline) {
        return RFC5424_PATTERN.matcher(rawline).matches();
    }

    @Override
    public LogObject parse(String rawline) {
        Matcher m = RFC5424_PATTERN.matcher(rawline);

        if (!m.matches()) {
            return null;
        }

        long epochTime = 0;
        String host = "";
        String pid = "";
        String msg = "";

        try {
            // Group 1: Timestamp (e.g., 2026-04-05T10:23:14.123Z)
            epochTime = java.time.OffsetDateTime.parse(m.group(1)).toEpochSecond();

            // Group 2: Host
            host = m.group(2);

            // Group 3 is App Name (e.g., MSWinEventLog) and Group 4 is ProcID (PID)
            String appName = m.group(3);
            String procId = m.group(4);

            // Combine AppName and ProcID for the PID field if ProcID isn't just a dash "-"
            pid = procId.equals("-") ? appName : appName + "[" + procId + "]";

            // Group 5 is MsgID (usually "-"), Group 6 is the rest of the line (Structured Data + Message)
            msg = m.group(6);

            if (!isValidHost(host)) {
                return null;
            }

            ParseStatus.incrementRFC5424();
        } catch (Exception e) {
            System.err.println("Error parsing RFC5424 log: " + e.getMessage());
            return null;
        }

        String severity = "INFO";
        String category = "UNCATEGORIZED";

        // Raw log object
        LogObject logObject = new LogObject(epochTime, host, severity, category, pid, msg);

        // Categorize the log object
        return CategorizationMaster.categorize(logObject);
    }

    private boolean isValidHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }

        String h = host.trim();

        // 1. Must match standard hostname/IP characters.
        if (!h.matches("[A-Za-z0-9._-]+")) {
            return false;
        }

        // 2. Reject obvious false positives
        String lower = h.toLowerCase(Locale.ROOT);
        if (lower.equals("default") || lower.equals("operation") || lower.equals("service")) {
            return false;
        }

        return true;
    }
}