package Parsers.Heuristic;

import Interfaces.CategorizationMaster;
import SentryStack.LogObject;
import Interfaces.ParserMaster;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeuristicParser implements ParserMaster {

    // The ^ forces it to match ONLY at the beginning of the string.
    // (?:<\\d+>)? ignores syslog priority tags like <13> if they exist.
    private static final Pattern DATE_HUNTER = Pattern.compile(
            "^(?:<\\d+>)?\\s*(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}|[A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})"
    );

    @Override
    public boolean canParse(String rawline) {
        // The ultimate fallback. If it reaches here, we try to parse it.
        return true;
    }

    @Override
    public LogObject parse(String rawline) {
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

                // TODO: Parse dateStr to set epochTime

                // Safely chop off ONLY the matched prefix, leaving the rest of the line alone
                rawline = rawline.substring(dateMatcher.end()).trim();
            } else {
                // No timestamp at the very beginning, use current time
                epochTime = System.currentTimeMillis() / 1000;
            }

            // 2. Tokenize the remaining string
            String[] tokens = rawline.split("\\s+");
            List<String> messageTokens = new ArrayList<>();
            boolean hostFound = false;

            // 3. Score the tokens
            for (String token : tokens) {
                // If we haven't found a host yet, check if this token looks like one
                if (!hostFound && isLikelyHost(token)) {
                    host = token;
                    hostFound = true;
                } else {
                    // Everything else gets dumped into the message bucket
                    messageTokens.add(token);
                }
            }

            // Rebuild the message
            message = String.join(" ", messageTokens);

        } catch (Exception e) {
            System.err.println("Heuristic parser encountered an error, falling back to raw dump: " + e.getMessage());
            message = rawline;
        }

        String severity = "INFO"; // You could add logic to hunt for "ERROR" or "WARN" in the tokens
        String category = "HEURISTIC";

        LogObject logObject = new LogObject(epochTime, host, severity, category, pid, message);
        return CategorizationMaster.categorize(logObject);
    }

    private boolean isLikelyHost(String token) {
        // Strip common brackets that might surround an IP/Host
        String cleanToken = token.replaceAll("[\\[\\]()=:]", "");

        // Is it an IPv4 address?
        if (cleanToken.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) return true;

        // Is it a MAC address?
        if (cleanToken.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) return true;

        // Does it look like a standard hostname with no weird punctuation?
        if (cleanToken.matches("^[A-Za-z0-9.-]+$") && cleanToken.length() > 2) {
            String lower = cleanToken.toLowerCase();
            if (!lower.equals("info") && !lower.equals("error") && !lower.equals("warn")) {
                return true;
            }
        }
        return false;
    }
}