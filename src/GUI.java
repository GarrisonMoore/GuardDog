import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.*;

public class GUI extends JFrame {
    private static GUI myGui;

    // Helper method to format timestamps
    public static GUI getMyGui() {
        return myGui;
    }

    // A model used to manage and store a list of strings displayed in GUI components, such as JList.
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    // A graphical list component that displays a collection of hostnames or IPs
    private final JList<String> hostList = new JList<>(listModel);
    // A JTextPane component used to display the currently selected log entry in the GUI.
    private final JTextPane selectedLogDisplay = new JTextPane();
    // Represents a text pane component within the GUI for displaying live log updates.
    private final JTextPane liveLogDisplay = new JTextPane();
    // A JScrollPane component for the live log display.
    private final JScrollPane liveScroll = new JScrollPane(liveLogDisplay);
    // A tabbed pane used to display logs in different categories or views.
    private final JTabbedPane logTabs = new JTabbedPane();
    // Represents a button used for navigating back within the GUI.
    private final JButton backButton = new JButton("↩");
    // A text field used to input and search logs based on user-provided criteria.
    private final JTextField logSearchField = new JTextField();
    // Holds a list of live log entries currently being buffered in the GUI.
    private final List<LogObject> liveLogsBuffer = Collections.synchronizedList(new ArrayList<>());

    // Colors for GUI
    private final Color ACCENT_COLOR = new Color(0, 150, 255); // Electric Blue
    private final Color PANEL_BG = new Color(25, 25, 25);
    private final Color LOG_BG = new Color(15, 15, 15);
    private final Color LIST_BG = new Color(20, 20, 20);
    private final Color BORDER_COLOR = new Color(40, 40, 40);

    // Combo box for drop down category selection
    private final JComboBox<String> pivotBox = new JComboBox<>(new String[]{"Hostnames", "Category", "Severity", "Time"});
    // A text field for searching logs
    private final JTextField searchField = new JTextField();

    // Used for refreshing the display
    private int lastRenderedCount = 0;
    private String lastSelectedKey = null;

    // Used for filtering logs based on time and day
    private enum BrowseMode { DAYS, TIMES, OTHER }
    private BrowseMode browseMode = BrowseMode.OTHER;
    private java.time.LocalDate selectedDay = null;

    // This method enables the user to search for logs based on a specified query.
    private void applySidebarFilter() {
        String query = searchField.getText().trim().toLowerCase();
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
            String[] categories = {"ERRORS", "WARNINGS", "AUTH EVENTS", "AUDIT", "GROUP POLICY", "UNCATEGORIZED"};
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

    // GUI constructor
    public GUI() {
        setTitle("Guard Dog Processor - Log Management Console");
        setSize(1300, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(PANEL_BG);
        setLayout(new BorderLayout());

        Font mainFont = new Font("Segoe UI", Font.PLAIN, 14);
        Font monoFont = new Font("JetBrains Mono", Font.PLAIN, 13);
        if (new Font("JetBrains Mono", Font.PLAIN, 13).getFamily().equals("Dialog")) {
            monoFont = new Font("Monospaced", Font.PLAIN, 13);
        }

        hostList.setFont(mainFont);
        hostList.setBackground(LIST_BG);
        hostList.setFixedCellHeight(40);
        hostList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        hostList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
                if (isSelected) {
                    label.setBackground(new Color(30, 80, 150));
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(LIST_BG);
                    label.setForeground(new Color(200, 200, 200));
                }
                return label;
            }
        });

        configureLogPane(selectedLogDisplay, monoFont);
        configureLogPane(liveLogDisplay, monoFont);

        searchField.setFont(mainFont);
        searchField.putClientProperty("JTextField.placeholderText", "Search logs...");
        searchField.putClientProperty("JComponent.outline", ACCENT_COLOR);

        pivotBox.setFont(mainFont);
        pivotBox.putClientProperty("JComponent.outline", ACCENT_COLOR);

        logSearchField.setFont(mainFont);
        logSearchField.putClientProperty("JTextField.placeholderText", "Search within logs (e.g. error, 404)...");
        logSearchField.putClientProperty("JComponent.outline", ACCENT_COLOR);
        logSearchField.setPreferredSize(new Dimension(300, 35));

        backButton.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 16));
        backButton.setPreferredSize(new Dimension(35, 35));
        backButton.setFocusPainted(false);
        backButton.setEnabled(false);
        backButton.addActionListener(e -> {
            if ("Time".equals(pivotBox.getSelectedItem())) {
                if (browseMode == BrowseMode.TIMES) {
                    loadDays();
                    selectedDay = null;
                    backButton.setEnabled(false);
                    refreshDisplay();
                }
            }
        });

        // event listeners for search field and log search field
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applySidebarFilter(); }
            public void removeUpdate(DocumentEvent e) { applySidebarFilter(); }
            public void changedUpdate(DocumentEvent e) { applySidebarFilter(); }
        });
        logSearchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { triggerLogFilter(); }
            public void removeUpdate(DocumentEvent e) { triggerLogFilter(); }
            public void changedUpdate(DocumentEvent e) { triggerLogFilter(); }
        });
        // event listener for hosts
        hostList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshDisplay();
            }
        });
        pivotBox.addActionListener(e -> {
            String selected = (String) pivotBox.getSelectedItem();
            listModel.clear();
            selectedDay = null;
            browseMode = BrowseMode.OTHER;

            // searching logic
            if ("Hostnames".equals(selected)) {
                for (String host : IndexingEngine.getHostKeys()) listModel.addElement(host);
            } else if ("Category".equals(selected)) {
                String[] categories = {"ERRORS", "WARNINGS", "AUTH EVENTS", "AUDIT", "GROUP POLICY","REMOTE MANAGEMENT","UNCATEGORIZED"};
                for (String cat : categories) listModel.addElement(cat);
            } else if ("Severity".equals(selected)) {
                listModel.addElement("INFO");
                listModel.addElement("WARN");
                listModel.addElement("CRIT");
            } else if ("Time".equals(selected)) {
                loadDays();
            }
            backButton.setEnabled(false);
            refreshDisplay();
        });

        // Layout construction
        JPanel topBar = new JPanel(new BorderLayout(15, 0));
        topBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftControls.setOpaque(false);
        pivotBox.setPreferredSize(new Dimension(180, 40));
        leftControls.add(pivotBox);

        topBar.add(leftControls, BorderLayout.WEST);
        topBar.add(searchField, BorderLayout.CENTER);

        JPanel sidePanel = new JPanel(new BorderLayout(0, 10));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 10));
        sidePanel.setPreferredSize(new Dimension(300, 0));

        JPanel navHeader = new JPanel(new BorderLayout(10, 0));
        navHeader.setOpaque(false);
        JLabel navLabel = new JLabel("Navigation");
        navLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        navLabel.setForeground(new Color(150, 150, 150));
        navHeader.add(navLabel, BorderLayout.WEST);
        navHeader.add(backButton, BorderLayout.EAST);

        JScrollPane listScroll = new JScrollPane(hostList);
        listScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        listScroll.putClientProperty("JComponent.arc", 12);
        listScroll.getVerticalScrollBar().setUnitIncrement(16);

        sidePanel.add(navHeader, BorderLayout.NORTH);
        sidePanel.add(listScroll, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 20, 20));

        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setOpaque(false);
        logHeader.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        logTabs.setBorder(null);
        logTabs.setOpaque(false);

        JPanel logSearchWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        logSearchWrap.setOpaque(false);
        logSearchWrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        logSearchWrap.add(logSearchField);
        logTabs.putClientProperty("JTabbedPane.trailingComponent", logSearchWrap);

        JScrollPane selectedScroll = new JScrollPane(selectedLogDisplay);
        selectedScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        selectedScroll.putClientProperty("JComponent.arc", 12);
        selectedScroll.getVerticalScrollBar().setUnitIncrement(16);

        liveScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        liveScroll.putClientProperty("JComponent.arc", 12);
        liveScroll.getVerticalScrollBar().setUnitIncrement(16);

        logTabs.addTab("SELECTED LOGS", selectedScroll);
        logTabs.addTab("LIVE FEED", liveScroll);

        centerPanel.add(logHeader, BorderLayout.NORTH);
        centerPanel.add(logTabs, BorderLayout.CENTER);

        add(topBar, BorderLayout.NORTH);
        add(sidePanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);

        setLocationRelativeTo(null);
        setVisible(true);
        myGui = this;
        loadDays();
    }

    // Configures the appearance of the log display pane
    private void configureLogPane(JTextPane pane, Font font) {
        pane.setFont(font);
        pane.setBackground(LOG_BG);
        pane.setEditable(false);
        pane.setCaretColor(ACCENT_COLOR);
        pane.setForeground(Color.WHITE);
        pane.setMargin(new Insets(8, 8, 8, 8));
    }

    // loads available days for browsing logs
    private void loadDays() {
        browseMode = BrowseMode.DAYS;
        listModel.clear();
        for (LocalDate day : IndexingEngine.getAvailableDays()) {
            listModel.addElement(day.toString());
        }
    }

    // loads available times for a given day
    private void loadTimesForDay(LocalDate day) {
        browseMode = BrowseMode.TIMES;
        selectedDay = day;
        listModel.clear();
        for (LocalTime time : IndexingEngine.getAvailableTimes(day)) {
            listModel.addElement(time.toString().substring(0, 5));
        }
        backButton.setEnabled(true);
    }

    // triggers log filtering based on current selection
    private void triggerLogFilter() {
        refreshDisplay();
        renderLogsToPane(liveLogDisplay, new ArrayList<>(liveLogsBuffer));
    }

    // sets the available hosts for filtering
    public void setHosts(Set<String> hosts) {
        if (!"Hostnames".equals(pivotBox.getSelectedItem())) return;
        String currentSelection = hostList.getSelectedValue();
        applySidebarFilter();
        if (currentSelection != null && listModel.contains(currentSelection)) {
            hostList.setSelectedValue(currentSelection, true);
        }
    }

    // refreshes the display
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

    // appends incoming logs to the live log display pane
    public void appendLiveLog(LogObject log) {
        liveLogsBuffer.add(log);
        if (liveLogsBuffer.size() > 2000) {
            liveLogsBuffer.remove(0);
        }
        SwingUtilities.invokeLater(() -> {
            String query = logSearchField.getText().trim().toLowerCase();
            if (query.isEmpty() || log.getMessage().toLowerCase().contains(query)) {
                JScrollBar scrollBar = liveScroll.getVerticalScrollBar();
                boolean isAtBottom = (scrollBar.getValue() + scrollBar.getVisibleAmount() >= scrollBar.getMaximum() - 50);

                StyledDocument doc = liveLogDisplay.getStyledDocument();
                appendColoredLog(doc, log);

                if (isAtBottom) {
                    liveLogDisplay.setCaretPosition(doc.getLength());
                }
            }
        });
    }

    // renders incoming logs to the selected log display pane
    private void renderLogsToPane(JTextPane pane, List<LogObject> logs) {
        StyledDocument doc = pane.getStyledDocument();
        pane.setText("");
        String query = logSearchField.getText().trim().toLowerCase();
        for (LogObject log : logs) {
            if (query.isEmpty() || log.getMessage().toLowerCase().contains(query)) {
                appendColoredLog(doc, log);
            }
        }
    }

    // styling the log text
    private void appendColoredLog(StyledDocument doc, LogObject log) {
        try {
            String timestamp = formatTimestamp(log);
            String host = log.getSource();
            String severity = log.getSeverity();
            String category = log.getCategory();
            String message = log.getMessage();

            Style tsStyle = selectedLogDisplay.addStyle("timestamp", null);
            StyleConstants.setForeground(tsStyle, new Color(110, 110, 110)); // Subdued

            Style hostStyle = selectedLogDisplay.addStyle("host", null);
            StyleConstants.setForeground(hostStyle, ACCENT_COLOR);
            StyleConstants.setBold(hostStyle, true);

            Style sevStyle = selectedLogDisplay.addStyle("severity", null);
            if ("CRIT".equalsIgnoreCase(severity)) {
                StyleConstants.setForeground(sevStyle, new Color(255, 80, 80));
                StyleConstants.setBold(sevStyle, true);
            } else if ("WARN".equalsIgnoreCase(severity)) {
                StyleConstants.setForeground(sevStyle, new Color(255, 200, 50));
            } else {
                StyleConstants.setForeground(sevStyle, new Color(100, 200, 255));
            }

            Style catStyle = selectedLogDisplay.addStyle("category", null);
            StyleConstants.setForeground(catStyle, new Color(180, 180, 180));

            Style msgStyle = selectedLogDisplay.addStyle("message", null);
            StyleConstants.setForeground(msgStyle, new Color(220, 220, 220));

            doc.insertString(doc.getLength(), timestamp + " ", tsStyle);
            doc.insertString(doc.getLength(), host + " ", hostStyle);
            doc.insertString(doc.getLength(), severity + " ", sevStyle);
            doc.insertString(doc.getLength(), "[" + category + "] ", catStyle);
            doc.insertString(doc.getLength(), message + "\n", msgStyle);
        } catch (BadLocationException ignored) {
        }
    }

    // helper to format timestamps
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