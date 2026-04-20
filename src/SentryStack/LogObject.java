package SentryStack;

/**
 * LogObject represents a single structured log entry.
 */
public class LogObject {
    private final long timestamp;
    private final String source;
    private String severity;
    private String category;
    private final String pid;
    private final String message;

    /**
     * Constructs a new LogObject with the specified details.
     * @param timestamp Epoch timestamp of the log entry.
     * @param source Source/host of the log entry.
     * @param severity Severity level (e.g., INFO, WARN, CRIT).
     * @param category Log category (e.g., SECURITY, NETWORK).
     * @param pid Process ID associated with the log entry.
     * @param message The log message content.
     */
    public LogObject(long timestamp, String source, String severity, String category, String pid, String message) {
        this.timestamp = timestamp;
        this.source = source;
        this.severity = severity;
        this.category = category;
        this.pid = pid;
        this.message = message;
    }

    // Helpers below
    /**
     * Returns the epoch timestamp of the log entry.
     * @return The timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the source/host of the log entry.
     * @return The source.
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the category of the log entry.
     * @return The category.
     */
    public String getCategory() {
        return category;
    }

    /**
     * Returns the process ID associated with the log entry.
     * @return The PID.
     */
    public String getPid() {
        return pid;
    }

    /**
     * Returns the log message content.
     * @return The message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the severity level of the log entry.
     * @return The severity.
     */
    public String getSeverity() {
        return severity;
    }

    /**
     * Sets the severity of the log entry.
     * @param severity The new severity level.
     */
    public void setSeverity(String severity){
        this.severity = severity;
    }

    /**
     * Sets the category of the log entry.
     * @param category The new category.
     */
    public void setCategory(String category){
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogObject logObject = (LogObject) o;
        return timestamp == logObject.timestamp &&
                java.util.Objects.equals(source, logObject.source) &&
                java.util.Objects.equals(severity, logObject.severity) &&
                java.util.Objects.equals(category, logObject.category) &&
                java.util.Objects.equals(pid, logObject.pid) &&
                java.util.Objects.equals(message, logObject.message);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(timestamp, source, severity, category, pid, message);
    }

    public String toString() {

        java.time.LocalDateTime date = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(timestamp),
                java.time.ZoneId.systemDefault()
        );

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return "[" + date.format(formatter) + "] " + source + " | " + severity + " | "+category+ " | " + pid+ " : " + message;
    }
}
