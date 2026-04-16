package GUI;

import SentryStack.IndexingEngine;
import SentryStack.LogObject;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;

public class GUI extends JFrame {
    private static GUI myGui;

    private final SidebarPanel sidebar;
    private final SelectedLogsPanel selectedLogsPanel;
    private final LiveFeedPanel liveFeedPanel;
    private final JButton pauseLiveFeedButton = new JButton("PAUSE LIVE FEED");

    private final JTabbedPane logTabs = new JTabbedPane();

    // Track state for CPU optimization
    private String lastSelectedKey = null;
    private String lastSelectedPivot = null;
    private int lastRenderedCount = -1;

    public GUI() {
        setTitle("Guard Dog Processor - Log Management Console");
        setSize(1600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(GUIConstants.PANEL_BG);
        setLayout(new BorderLayout());

        sidebar = new SidebarPanel(this);
        selectedLogsPanel = new SelectedLogsPanel(this);
        liveFeedPanel = new LiveFeedPanel();

        initComponents();
        setupListeners();

        sidebar.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
        add(sidebar, BorderLayout.WEST);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 20, 20));

        logTabs.setFont(GUIConstants.MAIN_FONT);
        logTabs.setBackground(GUIConstants.PANEL_BG);
        logTabs.setBorder(null);
        logTabs.setOpaque(false);
        logTabs.putClientProperty("JTabbedPane.tabType", "card");
        logTabs.putClientProperty("JTabbedPane.showTabSeparators", true);
        logTabs.putClientProperty("JTabbedPane.hasFullBorder", true);

        logTabs.addTab("SELECTED LOGS", selectedLogsPanel.getScroll());
        logTabs.addTab("LIVE FEED", liveFeedPanel.getScroll());

        // Use a FlowLayout for the trailing component (Search Field)
        JPanel trailingContent = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        trailingContent.setOpaque(false);

        // Style the pause button to look like a tab
        pauseLiveFeedButton.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        pauseLiveFeedButton.setFont(GUIConstants.NAV_LABEL_FONT);
        pauseLiveFeedButton.putClientProperty("JButton.buttonType", "square");
        pauseLiveFeedButton.putClientProperty("JComponent.arc", 12);
        pauseLiveFeedButton.setUI(new com.formdev.flatlaf.ui.FlatButtonUI(false) {
            @Override
            protected void paintBackground(Graphics g, JComponent c) {
                if (((JButton) c).isContentAreaFilled()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(c.getBackground());
                        int arc = 12;
                        int width = c.getWidth();
                        int height = c.getHeight();
                        g2.fillRoundRect(0, 0, width, height, arc, arc);
                        g2.fillRect(0, height / 2, width, (height / 2) + 1);
                    } finally {
                        g2.dispose();
                    }
                }
            }
        });
        pauseLiveFeedButton.setFocusPainted(false);
        pauseLiveFeedButton.setContentAreaFilled(true);
        pauseLiveFeedButton.setOpaque(true);
        pauseLiveFeedButton.setPreferredSize(new Dimension(180, 45));
        pauseLiveFeedButton.setBackground(GUIConstants.SUCCESS_COLOR);
        pauseLiveFeedButton.setForeground(Color.WHITE);

        trailingContent.add(pauseLiveFeedButton);
        trailingContent.add(Box.createHorizontalStrut(20));
        trailingContent.add(selectedLogsPanel.getSearchField());

        logTabs.putClientProperty("JTabbedPane.trailingComponent", trailingContent);

        centerPanel.add(logTabs, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        setLocationRelativeTo(null);
        setVisible(true);
        myGui = this;
    }

    private void initComponents() {
        // Additional component setup if needed
    }

    private void setupListeners() {
        sidebar.addSearchDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                sidebar.applySidebarFilter();
            }

            public void removeUpdate(DocumentEvent e) {
                sidebar.applySidebarFilter();
            }

            public void changedUpdate(DocumentEvent e) {
                sidebar.applySidebarFilter();
            }
        });

        pauseLiveFeedButton.addActionListener(e -> {
            boolean nextPaused = !liveFeedPanel.isPaused();
            liveFeedPanel.setPaused(nextPaused);
            setLiveFeedPaused(nextPaused);
        });
    }

    public void setLiveFeedPaused(boolean paused) {
        pauseLiveFeedButton.setText(paused ? "RESUME LIVE FEED" : "PAUSE LIVE FEED");
        pauseLiveFeedButton.setBackground(paused ? GUIConstants.CRIT_COLOR : GUIConstants.SUCCESS_COLOR);
        pauseLiveFeedButton.setForeground(Color.WHITE);
    }

    public static GUI getMyGui() {
        return myGui;
    }

    public SidebarPanel getSidebar() {
        return sidebar;
    }

    public void setHosts(Set<String> hosts) {
        // Optional: logic to update sidebar hosts if needed, though IndexingEngine is used directly
    }

    public void refreshDisplay() {
        String selectedKey = sidebar.getSelectedKey();
        String currentPivot = sidebar.getSelectedPivot();

        // CPU SAVER: If selection hasn't changed, don't re-render everything
        if (java.util.Objects.equals(selectedKey, lastSelectedKey) && 
            java.util.Objects.equals(currentPivot, lastSelectedPivot)) {
            
            // Check if count changed for the current selection
            int currentCount = getCountForSelection(selectedKey, currentPivot);
            if (currentCount == lastRenderedCount) {
                return;
            }
        }
        lastSelectedKey = selectedKey;
        lastSelectedPivot = currentPivot;

        if (selectedKey == null) {
            lastRenderedCount = 0;
            selectedLogsPanel.renderLogs(new ArrayList<>());
            return;
        }

        List<LogObject> logsToDisplay = new ArrayList<>();
        if ("Hostnames".equals(currentPivot)) {
            logsToDisplay = IndexingEngine.getLogsForHost(selectedKey);
        } else if ("Category".equals(currentPivot)) {
            logsToDisplay = IndexingEngine.getLogsByCategory(selectedKey);
        } else if ("Severity".equals(currentPivot)) {
            logsToDisplay = IndexingEngine.getLogsBySeverity(selectedKey);
        } else if ("Time".equals(currentPivot)) {
            if (sidebar.isTimesMode()) {
                LocalDate day = sidebar.getSelectedDay();
                LocalTime time = LocalTime.parse(selectedKey.length() == 5 ? selectedKey + ":00" : selectedKey);
                logsToDisplay = new ArrayList<>(IndexingEngine.TimeIndex
                        .getOrDefault(day, new java.util.concurrent.ConcurrentSkipListMap<>())
                        .getOrDefault(time.withSecond(0).withNano(0), java.util.Collections.emptySet()));
            } else {
                LocalDate day = LocalDate.parse(selectedKey);
                logsToDisplay = IndexingEngine.getLogsByDay(day);
            }
        }

        // Ensure logs are ordered by timestamp (Ascending)
        logsToDisplay.sort(java.util.Comparator.comparingLong(LogObject::getTimestamp));

        lastRenderedCount = logsToDisplay.size();
        selectedLogsPanel.renderLogs(logsToDisplay);
    }

    private int getCountForSelection(String key, String pivot) {
        if (key == null) return 0;
        if ("Hostnames".equals(pivot)) return IndexingEngine.getLogsForHost(key).size();
        if ("Category".equals(pivot)) return IndexingEngine.getLogsByCategory(key).size();
        if ("Severity".equals(pivot)) return IndexingEngine.getLogsBySeverity(key).size();
        if ("Time".equals(pivot)) {
            if (sidebar.isTimesMode()) {
                LocalDate day = sidebar.getSelectedDay();
                LocalTime time = LocalTime.parse(key.length() == 5 ? key + ":00" : key);
                return IndexingEngine.TimeIndex
                        .getOrDefault(day, new java.util.concurrent.ConcurrentSkipListMap<>())
                        .getOrDefault(time.withSecond(0).withNano(0), java.util.Collections.emptySet()).size();
            } else {
                return IndexingEngine.getLogsByDay(LocalDate.parse(key)).size();
            }
        }
        return -1;
    }

    public void refreshLiveFeed() {
        liveFeedPanel.refreshDisplay();
    }

    public void appendLiveLog(LogObject log) {
        liveFeedPanel.appendLiveLog(log);

        // REMOVED: SwingUtilities.invokeLater(sidebar::applySidebarFilter);
        // REMOVED: The logic checking shouldRefresh and forcing a display refresh per log
    }

    public SelectedLogsPanel getSelectedLogsPanel() {
        return selectedLogsPanel;
    }
}