package Parsers.JSON;

import Interfaces.CategorizationMaster;
import Interfaces.ParserMaster;
import SentryStack.LogObject;
import Interfaces.ParseStatus;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class JSONParser implements ParserMaster {

    int JSON_LOG_COUNT = 0;

    // Matches the "EventTime":"2026-04-03 15:00:21" format from your JSON payload
    private static final DateTimeFormatter JSON_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean canParse(String rawline) {
        // Check if the string contains at least one opening and closing brace
        // AND looks like it has JSON key-value pairs (not just GUIDs in braces)
        if (rawline == null || !rawline.contains("{") || !rawline.contains("}")) {
            return false;
        }
        // Extract the candidate JSON portion and check for key-value pattern
        int start = rawline.indexOf('{');
        int end = rawline.lastIndexOf('}');
        if (start >= end) return false;
        String candidate = rawline.substring(start, end + 1);
        return candidate.contains("\":");
    }

    @Override
    public LogObject parse(String rawline) {
        try {
            // Find the index of the first '{' and the last '}' to bypass the syslog preamble
            int startIndex = rawline.indexOf('{'); //
            int endIndex = rawline.lastIndexOf('}'); //

            // Ensure valid indices were found
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) { //
                ParseStatus.incrementJSON();

                // Extract just the JSON portion of the string
                String jsonPart = rawline.substring(startIndex, endIndex + 1); //

                // Parse the clean JSON string
                JsonObject jsonObject = JsonParser.parseString(jsonPart).getAsJsonObject(); //

                // --- 1. Extract Fields Safely ---
                long epochTime = 0;
                if (jsonObject.has("EventTime")) {
                    try {
                        String eventTimeStr = jsonObject.get("EventTime").getAsString();
                        epochTime = LocalDateTime.parse(eventTimeStr, JSON_DATE_FORMATTER)
                                .atZone(ZoneId.systemDefault()).toEpochSecond();
                    } catch (Exception e) {
                        System.err.println("Error parsing JSON EventTime: " + e.getMessage());
                    }
                }

                String host = jsonObject.has("Hostname") ? jsonObject.get("Hostname").getAsString() : "UNKNOWN";
                String pid = jsonObject.has("ProcessID") ? jsonObject.get("ProcessID").getAsString() : "";

                // If the JSON log has an EventID, let's inject it into the message so CategorizationMaster can see it
                String eventIdStr = jsonObject.has("EventID") ? "eventid: " + jsonObject.get("EventID").getAsString() + " " : "";

                String rawMsg = jsonObject.has("LogString") ? jsonObject.get("LogString").getAsString() : jsonPart;
                String msg = eventIdStr + rawMsg; // Prepending EventID so CategorizationMaster picks it up!

                // These will be overridden by CategorizationMaster if rules match
                String severity = "INFO";
                String category = "UNCATEGORIZED";

                // --- 2. Build and Categorize LogObject ---
                LogObject logObject = new LogObject(epochTime, host, severity, category, pid, msg);
                return CategorizationMaster.categorize(logObject); //
            }
        } catch (JsonSyntaxException e) { //
            System.err.println("Failed to parse JSON segment: " + e.getMessage()); //
        }
        return null;
    }
}