import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.RandomAccessFile;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.*;

public class GUI extends JFrame {
    private static GUI myGui;

    public static GUI getMyGui() {
        return myGui;
    }

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> hostList = new JList<>(listModel);
    private final JTextPane selectedLogDisplay = new JTextPane();
    private final JTextPane liveLogDisplay = new JTextPane();
    private final JTabbedPane logTabs = new JTabbedPane();
    private final JButton backButton = new JButton("← Back");

    private final JComboBox<String> pivotBox = new JComboBox<>(new String[]{"Hostnames", "Category", "Severity", "Time"});

    private int lastRenderedCount = 0;
    private String lastSelectedKey = null;

    private enum BrowseMode { DAYS, TIMES, OTHER }
    private BrowseMode browseMode = BrowseMode.OTHER;
    private java.time.LocalDate selectedDay = null;

    public GUI() {
        setTitle("Guard Dog NOC - In-memory Indexer and Datastore");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        Font nocFont = new Font("Monospaced", Font.BOLD, 12);
        hostList.setFont(nocFont);
        hostList.setBackground(Color.BLACK);
        hostList.setForeground(Color.GREEN);

        configureLogPane(selectedLogDisplay, nocFont);
        configureLogPane(liveLogDisplay, nocFont);

        JTextField searchField = new JTextField();
        searchField.setFont(new Font("Monospaced", Font.BOLD, 16));
        searchField.setBackground(Color.DARK_GRAY);
        searchField.setForeground(Color.WHITE);

        pivotBox.setFont(new Font("Monospaced", Font.BOLD, 16));

        backButton.setFont(new Font("Monospaced", Font.BOLD, 14));
        backButton.setFocusPainted(false);
        backButton.setVisible(false);
        backButton.addActionListener(e -> {
            if ("Time".equals(pivotBox.getSelectedItem())) {
                if (browseMode == BrowseMode.TIMES) {
                    loadDays();
                    selectedDay = null;
                    backButton.setVisible(false);
                    refreshDisplay();
                }
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterSidebar(searchField); }
            public void removeUpdate(DocumentEvent e) { filterSidebar(searchField); }
            public void changedUpdate(DocumentEvent e) { filterSidebar(searchField); }

            private void filterSidebar(JTextField field) {
                String query = field.getText().trim().toLowerCase();
                String currentPivot = (String) pivotBox.getSelectedItem();
                listModel.clear();

                if ("Time".equals(currentPivot)) {
                    if (browseMode == BrowseMode.DAYS) {
                        for (LocalDate day : IndexingEngine.getAvailableDays()) {
                            String value = day.toString();
                            if (value.contains(query)) listModel.addElement(value);
                        }
                    } else if (browseMode == BrowseMode.TIMES && selectedDay != null) {
                        for (LocalTime time : IndexingEngine.getAvailableTimes(selectedDay)) {
                            String value = time.toString().substring(0, 5);
                            if (value.contains(query)) listModel.addElement(value);
                        }
                    }
                } else if ("Hostnames".equals(currentPivot)) {
                    for (String host : IndexingEngine.getHostKeys()) {
                        if (host.toLowerCase().contains(query)) listModel.addElement(host);
                    }
                } else if ("Category".equals(currentPivot)) {
                    String[] categories = {"AUTH EVENTS", "AUDIT", "GROUP POLICY", "UNCATEGORIZED"};
                    for (String cat : categories) {
                        if (cat.toLowerCase().contains(query)) listModel.addElement(cat);
                    }
                } else if ("Severity".equals(currentPivot)) {
                    String[] severities = {"INFO", "WARN", "CRIT"};
                    for (String sev : severities) {
                        if (sev.toLowerCase().contains(query)) listModel.addElement(sev);
                    }
                }
            }
        });

        hostList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                lastRenderedCount = 0;
                lastSelectedKey = null;
                refreshDisplay();
            }
        });

        pivotBox.addActionListener(e -> {
            String selected = (String) pivotBox.getSelectedItem();
            listModel.clear();
            selectedDay = null;
            browseMode = BrowseMode.OTHER;

            if ("Hostnames".equals(selected)) {
                for (String host : IndexingEngine.getHostKeys()) listModel.addElement(host);
            } else if ("Category".equals(selected)) {
                listModel.addElement("AUTH EVENTS");
                listModel.addElement("AUDIT");
                listModel.addElement("GROUP POLICY");
                listModel.addElement("UNCATEGORIZED");
            } else if ("Severity".equals(selected)) {
                listModel.addElement("INFO");
                listModel.addElement("WARN");
                listModel.addElement("CRIT");
            } else if ("Time".equals(selected)) {
                loadDays();
            }
            backButton.setVisible(false);
            refreshDisplay();
        });

        JPanel topBar = new JPanel(new GridLayout(2, 1, 0, 8));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        topBar.add(pivotBox);
        topBar.add(searchField);

        javax.swing.border.Border lineBorder = BorderFactory.createLineBorder(Color.DARK_GRAY, 2);
        Font borderFont = new Font("Monospaced", Font.BOLD, 14);

        JScrollPane listScroll = new JScrollPane(hostList);
        listScroll.setBorder(BorderFactory.createTitledBorder(lineBorder, " Navigation ", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, borderFont, Color.LIGHT_GRAY));

        JScrollPane selectedScroll = new JScrollPane(selectedLogDisplay);
        selectedScroll.setBorder(BorderFactory.createTitledBorder(lineBorder, " Selected Logs ", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, borderFont, Color.LIGHT_GRAY));

        JScrollPane liveScroll = new JScrollPane(liveLogDisplay);
        liveScroll.setBorder(BorderFactory.createTitledBorder(lineBorder, " Live Logs ", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, borderFont, Color.LIGHT_GRAY));

        logTabs.addTab("Selected View", selectedScroll);
        logTabs.addTab("Live View", liveScroll);

        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(300, 0));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 5));

        JPanel navHeader = new JPanel(new BorderLayout());
        navHeader.add(backButton, BorderLayout.NORTH);
        navHeader.add(listScroll, BorderLayout.CENTER);

        sidePanel.add(navHeader, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 10));
        centerPanel.add(logTabs, BorderLayout.CENTER);

        add(topBar, BorderLayout.NORTH);
        add(sidePanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);

        setVisible(true);
        myGui = this;
        loadDays();
    }

    private void configureLogPane(JTextPane pane, Font font) {
        pane.setFont(font);
        pane.setBackground(Color.BLACK);
        pane.setEditable(false);
        pane.setCaretColor(Color.WHITE);
        pane.setForeground(Color.WHITE);
        pane.setMargin(new Insets(8, 8, 8, 8));
    }

    private void loadDays() {
        browseMode = BrowseMode.DAYS;
        listModel.clear();
        for (LocalDate day : IndexingEngine.getAvailableDays()) {
            listModel.addElement(day.toString());
        }
    }

    private void loadTimesForDay(LocalDate day) {
        browseMode = BrowseMode.TIMES;
        selectedDay = day;
        listModel.clear();
        for (LocalTime time : IndexingEngine.getAvailableTimes(day)) {
            listModel.addElement(time.toString().substring(0, 5));
        }
        backButton.setVisible(true);
    }

    public void setHosts(Set<String> hosts) {
        if (!"Hostnames".equals(pivotBox.getSelectedItem())) return;
        String currentSelection = hostList.getSelectedValue();
        listModel.clear();
        for (String h : hosts) listModel.addElement(h);
        if (currentSelection != null && hosts.contains(currentSelection)) {
            hostList.setSelectedValue(currentSelection, true);
        }
    }

    public void refreshDisplay() {
        String selected = hostList.getSelectedValue();
        String currentPivot = (String) pivotBox.getSelectedItem();

        if (selected == null) {
            if ("Time".equals(currentPivot)) {
                renderLogsToPane(selectedLogDisplay, new ArrayList<>());
            }
            return;
        }

        String selectionKey = currentPivot + "::" + selected;
        if (!Objects.equals(lastSelectedKey, selectionKey)) {
            lastRenderedCount = 0;
            lastSelectedKey = selectionKey;
        }

        List<LogObject> logs = new ArrayList<>();

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
                if (browseMode == BrowseMode.DAYS) {
                    selectedDay = LocalDate.parse(selected);
                    loadTimesForDay(selectedDay);
                    logs = IndexingEngine.getLogsByDay(selectedDay);
                } else if (browseMode == BrowseMode.TIMES && selectedDay != null) {
                    LocalTime time = LocalTime.parse(selected.length() == 5 ? selected + ":00" : selected);
                    logs = IndexingEngine.TimeIndex
                            .getOrDefault(selectedDay, new java.util.TreeMap<>())
                            .getOrDefault(time.withSecond(0).withNano(0), new ArrayList<>());
                }
                break;
        }

        renderLogsToPane(selectedLogDisplay, logs);
    }

    public void appendLiveLog(LogObject log) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = liveLogDisplay.getStyledDocument();
            appendColoredLog(doc, log);
            liveLogDisplay.setCaretPosition(doc.getLength());
        });
    }

    private void renderLogsToPane(JTextPane pane, List<LogObject> logs) {
        StyledDocument doc = pane.getStyledDocument();
        pane.setText("");
        for (LogObject log : logs) {
            appendColoredLog(doc, log);
        }
    }

    private void appendColoredLog(StyledDocument doc, LogObject log) {
        try {
            String timestamp = formatTimestamp(log);
            String host = log.getSource();
            String severity = log.getSeverity();
            String category = log.getCategory();
            String message = log.getMessage();

            Style tsStyle = selectedLogDisplay.addStyle("timestamp", null);
            StyleConstants.setForeground(tsStyle, Color.GREEN);

            Style hostStyle = selectedLogDisplay.addStyle("host", null);
            StyleConstants.setForeground(hostStyle, Color.GREEN);

            Style sevStyle = selectedLogDisplay.addStyle("severity", null);
            if ("CRIT".equalsIgnoreCase(severity)) {
                StyleConstants.setForeground(sevStyle, Color.RED);
            } else if ("WARN".equalsIgnoreCase(severity)) {
                StyleConstants.setForeground(sevStyle, Color.YELLOW);
            } else {
                StyleConstants.setForeground(sevStyle, Color.CYAN);
            }

            Style catStyle = selectedLogDisplay.addStyle("category", null);
            StyleConstants.setForeground(catStyle, Color.ORANGE);

            Style msgStyle = selectedLogDisplay.addStyle("message", null);
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