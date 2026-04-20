package Parsers.Heuristic;

import Interfaces.CategorizationMaster;
import Interfaces.ParseStatus;
import SentryStack.LogObject;
import Interfaces.ParserMaster;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HeuristicParser is a flexible fallback parser that attempts to extract log data
 * from formats not explicitly covered by other parsers.
 */
public class HeuristicParser implements ParserMaster {

    // The ^ forces it to match ONLY at the beginning of the string.
    // (?:<\\d+>)? ignores syslog priority tags like <13> if they exist.
    // Safely eats Syslog priorities (<14>), version numbers (1), and timezone/milliseconds (.123Z)
    private static final Pattern DATE_HUNTER = Pattern.compile(
            "^(?:<\\d+>)?(?:\\s*\\d+)?\\s*(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}[\\.\\d+]*[A-Za-z:-]*|[A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})"
    );

    @Override
    public boolean canParse(String rawline) {
        // The ultimate fallback. If it reaches here, we try to parse it.
        return true;
    }

    /**
     * Attempts to parse a raw log line using heuristic matching.
     * @param rawline The raw log line.
     * @return A {@link LogObject} if parsing is successful, null otherwise.
     */
    @Override
    public LogObject parse(String rawline) {

        if (rawline == null || rawline.isBlank()) {
            System.out.println("DEBUG DROP [HEURISTIC] - Blank or Null line received.");
            return null;
        }

        if (rawline == null || rawline.isBlank()) return null;

        long epochTime = 0;
        String host = "UNKNOWN-HOST";
        String pid = "N/A";
        String message = rawline; // Default to whole line if we fail to extract

        try {
            // 1. Hunt for a Timestamp ONLY at the beginning
            Matcher dateMatcher = DATE_HUNTER.matcher(rawline);

            if (dateMatcher.find()) {
                String dateStr = dateMatcher.group(1);

                try {
                    if (dateStr.matches("^[A-Z][a-z]{2}.*")) {
                        // BSD Style: Apr  1 12:34:56
                        String bsdDate = dateStr.replaceAll("\\s+", " ");
                        epochTime = java.time.LocalDateTime.parse(bsdDate, new java.time.format.DateTimeFormatterBuilder()
                                        .appendPattern("MMM d HH:mm:ss")
                                        .parseDefaulting(java.time.temporal.ChronoField.YEAR, java.time.LocalDate.now().getYear())
                                        .toFormatter(java.util.Locale.ENGLISH))
                                .atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
                    } else {
                        // ISO Style: 2026-04-06T02:35:58.760684-07:00 or 2026-04-06 02:35:58
                        String isoDate = dateStr.replace(' ', 'T');
                        if (!isoDate.contains("+") && !isoDate.contains("-") && !isoDate.endsWith("Z")) {
                           epochTime = java.time.LocalDateTime.parse(isoDate).atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
                        } else {
                           epochTime = java.time.OffsetDateTime.parse(isoDate).toEpochSecond();
                        }
                    }
                } catch (Exception e) {
                    epochTime = System.currentTimeMillis() / 1000;
                }

                // Safely chop off ONLY the matched prefix, leaving the rest of the line alone
                rawline = rawline.substring(dateMatcher.end()).trim();
            } else {
                // No timestamp at the very beginning, use current time
                epochTime = System.currentTimeMillis() / 1000;
            }

            // 2. Tokenize the remaining string
            String[] tokens = rawline.split("\\s+");
            List<String> messageTokens = new ArrayList<>();

            // 3. Score ONLY the first token to see if it's a host
            if (tokens.length > 0) {
                if (isLikelyHost(tokens[0])) {
                    host = tokens[0];
                    // Replaces the "for (int i = 1; ...)" loop
                    messageTokens.addAll(Arrays.asList(tokens).subList(1, tokens.length));
                } else {
                    // Replaces the "for (int i = 0; ...)" loop
                    Collections.addAll(messageTokens, tokens);
                }
            }

            // Rebuild the message
            message = String.join(" ", messageTokens);

            // Rebuild the message
            message = String.join(" ", messageTokens);

        } catch (Exception e) {
            System.out.println("DEBUG DROP [HEURISTIC] - Exception: " + e.getMessage() + " | Raw: " + rawline);
            message = rawline;
        }

        String severity = "INFO"; // You could add logic to hunt for "ERROR" or "WARN" in the tokens
        String category = "PARSER-HEURISTIC"; // Temporary Pivotbox Category

        ParseStatus.incrementUniversal();
        LogObject logObject = new LogObject(epochTime, host, severity, category, pid, message);
        return CategorizationMaster.categorize(logObject);
    }

    /**
     * Determines if a token is likely to be a hostname or IP address.
     * @param token The string to evaluate.
     * @return True if the token resembles a network host.
     */
    private boolean isLikelyHost(String token) {
        // Strip common brackets that might surround an IP/Host
        String cleanToken = token.replaceAll("[\\[\\]()=:]", "");

        // Is it an IPv4 address?
        if (cleanToken.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) return true;

        // Is it a MAC address?
        if (cleanToken.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) return true;

        // Does it look like a Fully Qualified Domain Name (FQDN)? (e.g., host.domain.local)
        // Must contain at least one dot surrounded by alphanumeric characters
        if (cleanToken.matches("^[a-zA-Z0-9-]+\\.[a-zA-Z0-9.-]+$")) return true;

        // Does it look like a standard machine name?
        if (cleanToken.matches("^[A-Za-z0-9.-]+$") && cleanToken.length() > 2) {
            String lower = cleanToken.toLowerCase();
            if (lower.equals("info") || lower.equals("error") || lower.equals("warn") || lower.equals("debug")) {
                return false;
            }

            boolean hasLetter = cleanToken.matches(".*[A-Za-z].*");
            boolean hasNumber = cleanToken.matches(".*[0-9].*");
            boolean hasDash = cleanToken.contains("-");

            // If it is pure letters (like "The" or "Connection"), reject it.
            // A valid guessed hostname should contain a mix of letters AND a number or dash.
            if (hasLetter && (hasNumber || hasDash)) {
                return true;
            }
        }

        return false;
    }
}