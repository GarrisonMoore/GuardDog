import javax.swing.*;
import java.awt.*;


public class ReadmeGUI {

    // Colors for GUI
    private final Color ACCENT_COLOR = new Color(0, 150, 255); // Electric Blue
    private final Color PANEL_BG = new Color(25, 25, 25);
    private final Color LOG_BG = new Color(15, 15, 15);
    private final Color LIST_BG = new Color(20, 20, 20);
    private final Color BORDER_COLOR = new Color(40, 40, 40);

    private final JTextPane textPane = new JTextPane();
    private final JScrollPane scrollPane = new JScrollPane(textPane);

    private final JButton closeButton = new JButton("Close");

    private static ReadmeGUI GUI;

    public ReadmeGUI() {

        textPane.setBackground(LOG_BG);

        JFrame frame = new JFrame("Guard Dog Processor - ReadMe");

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(500, 800);
        frame.setLayout(new BorderLayout());

        textPane.setEditable(false);

        textPane.setContentType("text/html");

        // AI wrote this HTML. I have no desire to learn HTML.
        textPane.setText("""
                <html>
                  <body style="font-family:Segoe UI, Arial, sans-serif; background-color:#0f0f0f; color:#e6e6e6; margin:16px;">
                    <div style="max-width:760px;">
                      <h1 style="color:#0096ff; margin-bottom:6px;">Guard Dog Processor</h1>
                      <p style="color:#bdbdbd; font-size:13px; margin-top:0;">
                        A Log Aggregation and Monitoring System
                      </p>

                      <p>
                        The Guard Dog Processor is a Java-based log aggregation, parsing, and monitoring system designed to tail system logs in real-time.
                        It features a modern, dark-themed GUI for sorting, searching, and viewing logs via multiple filters such as Time, Hostname, Category, and Severity.
                        This application was designed in conjunction with the "Guard Dog NOC Bridge" project, a custom built Debian 13 syslog aggregator OS.
                      </p>

                      <h2 style="color:#0096ff;">Features</h2>
                      <ul>
                        <li><b>Real-Time Log Ingestion</b>: Tails a specified log file on a daemon thread and parses new entries immediately.</li>
                        <li><b>RFC-5424 Syslog Parsing</b>: Regex-based parser extracts timestamps, hosts, and categories.</li>
                        <li><b>Categorization &amp; Severity Mapping</b>: Automatically flags logs as <code>INFO</code>, <code>WARN</code>, or <code>CRIT</code>.</li>
                        <li><b>Advanced GUI</b>: Built with Swing and FlatDarkLaf for live log buffers and filtering.</li>
                      </ul>

                      <h2 style="color:#0096ff;">Project Structure</h2>
                      <ul>
                        <li><b>Main.java</b>: Application entry point.</li>
                        <li><b>GUI.java</b>: Primary Swing interface.</li>
                        <li><b>IndexingEngine.java</b>: Core data store and file-reading process.</li>
                        <li><b>LogObject.java</b>: Data model for a single log entry.</li>
                        <li><b>LogParser.java</b> &amp; <b>SyslogParser.java</b>: Parsing interfaces and implementations.</li>
                      </ul>

                      <h2 style="color:#0096ff;">Prerequisites</h2>
                      <ul>
                        <li>Java 8 or higher</li>
                        <li>FlatLaf</li>
                      </ul>

                      <h2 style="color:#0096ff;">Setup and Execution</h2>
                      <ol>
                        <li>Update the log file path in <code>Main.java</code>.</li>
                        <li>Compile the Java files with FlatLaf in the classpath.</li>
                        <li>Run the application.</li>
                      </ol>
                    </div>
                  </body>
                </html>
                """);

        closeButton.addActionListener(e -> frame.dispose());

        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollPane.setBackground(PANEL_BG);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(closeButton, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
    }

    public ReadmeGUI getGUI() {
        return GUI;
    }

}
