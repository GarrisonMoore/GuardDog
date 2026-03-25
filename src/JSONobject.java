import java.util.HashMap;

///  example JSON object class
public class JSONobject {
    private long timestamp;
    private String source;
    private String level;
    private String message;


    public JSONobject(long timestamp, String source, String level, String message) {
        this.timestamp = timestamp;
        this.source = source;
        this.level = level;
        this.message = message;
    }

    // Helpers below
    public long getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String toString() {

        java.time.LocalDateTime date = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(timestamp),
                java.time.ZoneId.systemDefault()
        );

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return "[" + date.format(formatter) + "] " + source + " | " + level + ": " + message;
    }
}

