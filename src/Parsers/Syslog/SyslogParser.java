package Parsers.Syslog;

import Interfaces.CategorizationMaster;
import SentryStack.LogObject;
import Interfaces.ParserMaster;
import Interfaces.ParseStatus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SyslogParser implements the ParserMaster interface to parse logs in RFC 5424 format.
 */
public class SyslogParser implements ParserMaster {

    // Matches true RFC-5424 format:
    // Optional <PRI>VERSION, then TIMESTAMP, HOST, APP, PID, MSGID (optional), and the rest (Structured Data + MSG)
    private static final Pattern RFC5424_PATTERN = Pattern.compile(
            "^(?:<\\d+>\\d+\\s+)?(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[^\\s]*)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)(?:\\s+(\\S+))?\\s+(.*)$"
    );

    @Override
    public boolean canParse(String rawline) {
        return RFC5424_PATTERN.matcher(rawline).matches();
    }

    /**
     * Attempts to parse a raw log line in RFC 5424 format.
     * @param rawline The raw log line.
     * @return A {@link LogObject} if parsing is successful, null otherwise.
     */
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
            String msgId = m.group(5);
            String rest = m.group(6);

            // 1. Identify the true PID and Start of Message
            // Many Windows logs use format: APPNAME[PID] MESSAGE... (RFC3164-ish but in RFC5424 timestamp)
            // Or APPNAME[PID] [USER] MESSAGE...
            
            if (appName.matches(".*\\d+\\]$") && !procId.equals("-")) {
                // appName already has PID.
                // Check if procId looks like a PID or a message start.
                if (procId.matches("^\\d+$")) {
                     // appName has one PID, but there's another one in procId? 
                     // This happens sometimes if the app name is like "Service[123]" and procId is "456".
                     // We'll combine them to be safe, but usually it's just one.
                     pid = appName + "[" + procId + "]";
                     msg = (msgId != null && !msgId.equals("-") ? msgId + " " : "") + rest;
                } else {
                     // procId is NOT a simple number, so it's likely part of the message.
                     pid = appName;
                     msg = procId + (msgId != null && !msgId.equals("-") ? " " + msgId : "") + " " + rest;
                }
            } else if (procId.equals("-")) {
                pid = appName;
                msg = (msgId != null && !msgId.equals("-") ? msgId + " " : "") + rest;
            } else {
                pid = appName + "[" + procId + "]";
                msg = (msgId != null && !msgId.equals("-") ? msgId + " " : "") + rest;
            }

            if (msg.contains("the locale specific resource for the desired message is not present")) {
                return null;
            }

            // Clean up double spaces if any
            msg = msg.trim().replaceAll("\\s+", " ");

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

    /**
     * Validates if the hostname or source in the log is a valid network identifier.
     * @param host The hostname or IP address to validate.
     * @return True if it matches valid host patterns.
     */
    private boolean isValidHost(String host) {
        if (host == null) return false;
        String h = host.trim();
        if (h.isEmpty()) return false;

        String lower = h.toLowerCase(Locale.ROOT);

        // Common words seen in messages that should never be hosts
        String[] stop = {"overall","remaining","logged","registration","stage","enqueue","dequeue","evaluation","flushing","bundle","post","data","machine","check"};
        for (String s : stop) {
            if (lower.equals(s)) return false;
        }

        // IPv4
        if (h.matches("^(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)){3}$")) return true;
        // IPv6 (very permissive)
        if (h.matches("^[0-9A-Fa-f:]{2,}$") && h.contains(":")) return true;
        // FQDN with at least one dot
        if (h.matches("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")) return true;
        // NetBIOS-like: allow letters/digits and dashes, require at least one digit or dash to avoid plain words
        if (h.matches("^[A-Za-z0-9-]{3,63}$")) {
            boolean hasDigit = h.matches(".*[0-9].*");
            boolean hasDash = h.contains("-");
            if (hasDigit || hasDash) return true;
            return false;
        }

        return false;
    }
}