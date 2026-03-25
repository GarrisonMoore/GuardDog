import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Objects;

/// bruh what
public class GUI extends JFrame {
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> hostList = new JList<>(listModel);
    private final JTextPane logDisplay = new JTextPane();

    private final JComboBox<String> pivotBox = new JComboBox<>(new String[]{"Hostnames", "Category", "Severity", "Time Window"});

    private int lastRenderedCount = 0;

    private String lastSelectedKey = null;

    public GUI() {
        setTitle("Guard Dog NOC - In-memory Indexer and Datastore");
        setSize(1200, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // High-Contrast NOC Styles
        Font nocFont = new Font("Monospaced", Font.BOLD, 12);
        hostList.setFont(nocFont);
        hostList.setBackground(Color.BLACK);
        hostList.setForeground(Color.GREEN);

        logDisplay.setFont(nocFont);
        logDisplay.setBackground(Color.BLACK);
        logDisplay.setEditable(false);
        logDisplay.setCaretColor(Color.WHITE);
        logDisplay.setForeground(Color.WHITE);
        logDisplay.setMargin(new Insets(8, 8, 8, 8));

        JTextField searchField = new JTextField();
        searchField.setFont(new Font("Monospaced", Font.BOLD, 16));
        searchField.setBackground(Color.DARK_GRAY);
        searchField.setForeground(Color.WHITE);

        // Create the dropdown before using it
        pivotBox.setFont(new Font("Monospaced", Font.BOLD, 16));

        // Add a DocumentListener to the search field
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            public void changedUpdate(DocumentEvent e) {
                filter();
            }

            private void filter() {
                String query = searchField.getText().toLowerCase();
                String currentPivot = (String) pivotBox.getSelectedItem();
                listModel.clear();

                if ("Hostnames".equals(currentPivot)) {
                    for (String host : IndexingEngine.getHostKeys()) {
                        if (host.toLowerCase().contains(query)) {
                            listModel.addElement(host);
                        }
                    }
                } else if ("Category".equals(currentPivot)) {
                    String[] categories = {"AUTH EVENTS", "AUDIT", "GROUP POLICY", "UNCATEGORIZED"};
                    for (String cat : categories) {
                        if (cat.toLowerCase().contains(query)) {
                            listModel.addElement(cat);
                        }
                    }
                } else if ("Severity".equals(currentPivot)) {
                    String[] severities = {"INFO", "WARN", "CRIT"};
                    for (String sev : severities) {
                        if (sev.toLowerCase().contains(query)) {
                            listModel.addElement(sev);
                        }
                    }
                } else if ("Time Window".equals(currentPivot)) {
                    String[] windows = {"Last 5 Minutes", "Last 30 Minutes", "Last Hour"};
                    for (String window : windows) {
                        if (window.toLowerCase().contains(query)) {
                            listModel.addElement(window);
                        }
                    }
                }
            }
        });

        // Selection Logic: fetch logs for the selected host
        hostList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                lastRenderedCount = 0;
                lastSelectedKey = null;
                refreshDisplay();
            }
        });

        // Now it is safe to attach the listener
        pivotBox.addActionListener(e -> {
            String selectedPivot = (String) pivotBox.getSelectedItem();
            String query = searchField.getText().trim();
            listModel.clear();

            if ("Hostnames".equals(selectedPivot)) {
                for (String host : IndexingEngine.getHostKeys()) {
                    if (host.toLowerCase().contains(query.toLowerCase())) {
                        listModel.addElement(host);
                    }
                }
            } else if ("Category".equals(selectedPivot)) {
                String[] categories = {"AUTH EVENTS", "AUDIT", "GROUP POLICY", "UNCATEGORIZED"};
                for (String cat : categories) {
                    if (cat.toLowerCase().contains(query.toLowerCase())) {
                        listModel.addElement(cat);
                    }
                }
            } else if ("Severity".equals(selectedPivot)) {
                String[] severities = {"INFO", "WARN", "CRIT"};
                for (String sev : severities) {
                    if (sev.toLowerCase().contains(query.toLowerCase())) {
                        listModel.addElement(sev);
                    }
                }
            } else if ("Time".equals(selectedPivot)) {
                if (!query.isBlank()) {
                    listModel.addElement(query);
                }
            }
        });

        // --- UI LAYOUT & BORDERS ---

        JPanel topBar = new JPanel(new GridLayout(2, 1, 0, 8));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding around the edges
        topBar.add(pivotBox);
        topBar.add(searchField);

        javax.swing.border.Border lineBorder = BorderFactory.createLineBorder(Color.DARK_GRAY, 2);
        Font borderFont = new Font("Monospaced", Font.BOLD, 14);

        // Wrap the list in a ScrollPane and give it a Titled Border
        JScrollPane listScroll = new JScrollPane(hostList);
        listScroll.setBorder(BorderFactory.createTitledBorder(
                lineBorder, " Navigation ",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                borderFont, Color.LIGHT_GRAY));

        JScrollPane logScroll = new JScrollPane(logDisplay);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                lineBorder, " Log Stream Output ",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                borderFont, Color.LIGHT_GRAY));

        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(300, 0)); // Slightly wider for the borders
        sidePanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 5)); // Left/Bottom margins
        sidePanel.add(listScroll, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 10)); // Right/Bottom margins
        centerPanel.add(logScroll, BorderLayout.CENTER);

        add(topBar, BorderLayout.NORTH);
        add(sidePanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);

        setVisible(true);
    }

    public void setHosts(Set<String> hosts) {
        if (!"Hostnames".equals(pivotBox.getSelectedItem())) return;

        String currentSelection = hostList.getSelectedValue(); // Remember what was clicked
        listModel.clear();
        for (String h : hosts) {
            listModel.addElement(h);
        }

        // Put the click back where it belongs
        if (currentSelection != null && hosts.contains(currentSelection)) {
            hostList.setSelectedValue(currentSelection, true);
        }
    }

    // 2. Add this to the bottom of GUI.java
    public void refreshDisplay() {
        String selected = hostList.getSelectedValue();
        if (selected == null) return; // Do nothing if nothing is clicked

        String currentPivot = (String) pivotBox.getSelectedItem();
        String selectionKey = currentPivot + "::" + selected;

        if (!Objects.equals(lastSelectedKey, selectionKey)) {
            lastRenderedCount = 0;
            lastSelectedKey = selectionKey;
            logDisplay.setText("");
        }

        List<LogObject> logs = new ArrayList<>();

        // Fetches logs matching selected pivot criteria
        switch (currentPivot) {
            case "Hostnames":
                logs = IndexingEngine.getLogsForHost(selected);
                break;
            case "Category":
                logs = IndexingEngine.getLogsByCategory(selected);
                break;
            case "Severity":
                logs = IndexingEngine.getLogsBySeverity(selected);
                break;
            case "Time":
               logs = searchByDateTime(selected);
        }

        StyledDocument doc = logDisplay.getStyledDocument();
        logDisplay.setText("");

        for (LogObject log : logs) {
            appendColoredLog(doc, log);
        }

        lastRenderedCount = logs.size();
    }

    private List<LogObject> searchByDateTime(String query) {
        try {
            // format: yyyy-MM-dd HH:mm-HH:mm
            String[] parts = query.split("\\s+");
            if (parts.length != 2) return new ArrayList<>();

            java.time.LocalDate day = java.time.LocalDate.parse(parts[0]);

            String[] timeParts = parts[1].split("-");
            if (timeParts.length != 2) return new ArrayList<>();

            java.time.LocalTime start = java.time.LocalTime.parse(timeParts[0]);
            java.time.LocalTime end = java.time.LocalTime.parse(timeParts[1]);

            return IndexingEngine.getLogsByDayAndTime(day, start, end);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private void appendColoredLog(StyledDocument doc, LogObject log) {
        try {
            String timestamp = formatTimestamp(log);
            String host = log.getSource();
            String severity = log.getSeverity();
            String category = log.getCategory();
            String message = log.getMessage();

            Style tsStyle = logDisplay.addStyle("timestamp", null);
            StyleConstants.setForeground(tsStyle, Color.GREEN);

            Style hostStyle = logDisplay.addStyle("host", null);
            StyleConstants.setForeground(hostStyle, Color.GREEN);

            Style sevStyle = logDisplay.addStyle("severity", null);
            if ("CRIT".equalsIgnoreCase(severity)) {
                StyleConstants.setForeground(sevStyle, Color.RED);
            } else if ("WARN".equalsIgnoreCase(severity)) {
                StyleConstants.setForeground(sevStyle, Color.YELLOW);
            } else {
                StyleConstants.setForeground(sevStyle, Color.CYAN);
            }

            Style catStyle = logDisplay.addStyle("category", null);
            StyleConstants.setForeground(catStyle, Color.ORANGE);

            Style msgStyle = logDisplay.addStyle("message", null);
            StyleConstants.setForeground(msgStyle, Color.WHITE);

            doc.insertString(doc.getLength(), "[", msgStyle);
            doc.insertString(doc.getLength(), timestamp, tsStyle);
            doc.insertString(doc.getLength(), "] ", msgStyle);
            doc.insertString(doc.getLength(), host, hostStyle);
            doc.insertString(doc.getLength(), " | ", msgStyle);
            doc.insertString(doc.getLength(), severity, sevStyle);
            doc.insertString(doc.getLength(), " | ", msgStyle);
            doc.insertString(doc.getLength(), category, catStyle);
            doc.insertString(doc.getLength(), " : ", msgStyle);
            doc.insertString(doc.getLength(), message + "\n", msgStyle);
        } catch (BadLocationException ignored) {
            // If insertion fails, skip the line instead of crashing the UI
        }
    }

    private String formatTimestamp(LogObject log) {
        java.time.LocalDateTime date = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(log.getTimestamp()),
                java.time.ZoneId.systemDefault()
        );
        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return date.format(formatter);
    }
}