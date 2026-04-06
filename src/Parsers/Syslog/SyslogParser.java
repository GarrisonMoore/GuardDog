package Parsers.Syslog;

import Interfaces.CategorizationMaster;
import SentryStack.LogObject;
import Interfaces.ParserMaster;
import Interfaces.ParseStatus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyslogParser implements ParserMaster {

    // Matches RFC-5424 format with optional second timestamp and optional version
    // Regex updated to be more flexible and handle trailing whitespace/newlines
    // Group 1 (Timestamp) MUST contain a 'T' to differentiate from fragments
    private static final Pattern RFC5424_PATTERN = Pattern.compile(
            "^(?:<\\d+>)?(?:\\d+\\s+)?(\\S+T\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(?:\\S+\\s+)?(.*?)\\s*$"
    );

    // List of severities to avoid over-stripping in cleaning logic
    private static final String SEVERITIES_REGEX = "(?:INFO|WARN|ERROR|DEBUG|CRITICAL|NOTICE|EMERG|ALERT|ERR|WARNING|FATAL)";

    @Override
    public boolean canParse(String rawline) {
        if (rawline == null || rawline.isBlank()) return false;

        // Check if it's an RFC5424 log first
        if (RFC5424_PATTERN.matcher(rawline).matches()) {
            return true;
        }

        // Only reject logs that start with a timestamp followed IMMEDIATELY by a severity
        // This usually indicates the second half of a log that was split by another process
        // BUT don't reject if there is a hostname/tabbed structure (handled by Heuristic)
        if (rawline.matches("^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\s+" + SEVERITIES_REGEX + ".*$")) {
            return false;
        }

        return false;
    }

    @Override
    public LogObject parse(String rawline) {

        if (rawline == null || rawline.isBlank()) {
            System.out.println("DEBUG DROP [RCF5424] - Blank or Null line received.");
            return null;
        }

        Matcher m = RFC5424_PATTERN.matcher(rawline);

        if (!m.matches()) {
            // DEBUG: The regex failed completely
            System.out.println("DEBUG DROP [RFC5424] - Regex Mismatch | Raw: " + rawline);
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

            // NXLog syslog_ietf fix: if msg contains another timestamp (like 2026-04-06...), strip it
            // The timestamp can be preceded by [MetaData] or similar structured data tags if they weren't matched by the regex
            // Using CASE_INSENSITIVE to ensure it works even if the message was somehow lowercased.
            // We use \s+ to match tabs OR spaces.
            // ONLY strip if it's followed by Host/Severity/Category to avoid stripping timestamp-like content.
            Pattern nxlogHeaderPattern = Pattern.compile("^(?:\\[.*?\\]\\s*)?\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}[\\.\\d+A-Z:-]*\\s+(?:\\S+\\s+)?(?:INFO|WARN|ERROR|DEBUG|CRITICAL|NOTICE|EMERG|ALERT|ERR|WARNING|FATAL)\\s+(?:SYSTEM & SERVICES|AUTH & ACCESS|NETWORK|POLICY & AUDIT|WARNINGS|SECURITY & ERRORS|UNCATEGORIZED)\\s+", Pattern.CASE_INSENSITIVE);
            Matcher nxMatcher = nxlogHeaderPattern.matcher(msg);
            if (nxMatcher.find()) {
                msg = msg.substring(nxMatcher.end());
            }

            // Also check for the AppName[PID] pattern that might be in the redundant header
            // BUT only strip if it's right at the beginning after we've already stripped the rest.
            Pattern pidPattern = Pattern.compile("^\\S+\\[\\d+\\](?:\\[\\S+\\])?\\s+", Pattern.CASE_INSENSITIVE);
            msg = pidPattern.matcher(msg).replaceFirst("");

            if (!isValidHost(host)) {
                // DEBUG: The host validation failed
                System.out.println("DEBUG DROP [RFC5424] - Invalid Host (" + host + ") | Raw: " + rawline);
                return null;
            }

            ParseStatus.incrementRFC5424();
        } catch (Exception e) {
            // DEBUG: Something threw a hard error
            System.out.println("DEBUG DROP [RFC5424] - Exception: " + e.getMessage() + " | Raw: " + rawline);
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