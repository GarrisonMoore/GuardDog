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

    private final TopBarPanel topBar;
    private final SidebarPanel sidebar;
    private final SelectedLogsPanel selectedLogsPanel;
    private final LiveFeedPanel liveFeedPanel;

    private final JTabbedPane logTabs = new JTabbedPane();

    public GUI() {
        setTitle("Guard Dog Processor - Log Management Console");
        setSize(1300, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(GUIConstants.PANEL_BG);
        setLayout(new BorderLayout());

        topBar = new TopBarPanel();
        sidebar = new SidebarPanel(this);
        selectedLogsPanel = new SelectedLogsPanel(this);
        liveFeedPanel = new LiveFeedPanel();

        initComponents();
        setupListeners();

        add(topBar, BorderLayout.NORTH);
        add(sidebar, BorderLayout.WEST);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 20, 20));

        logTabs.setBorder(null);
        logTabs.setOpaque(false);

        // Put the Pause Live Feed button in the tab header trailing component
        JPanel trailingWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        trailingWrap.setOpaque(false);
        trailingWrap.add(selectedLogsPanel.getSearchField());
        trailingWrap.add(topBar.getPauseButton());
        logTabs.putClientProperty("JTabbedPane.trailingComponent", trailingWrap);


        logTabs.addTab("SELECTED LOGS", selectedLogsPanel.getScroll());
        logTabs.addTab("LIVE FEED", liveFeedPanel.getScroll());

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
        topBar.addPivotActionListener(e -> sidebar.onPivotChanged(topBar.getSelectedPivot()));
        topBar.addSearchDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { sidebar.applySidebarFilter(); }
            public void removeUpdate(DocumentEvent e) { sidebar.applySidebarFilter(); }
            public void changedUpdate(DocumentEvent e) { sidebar.applySidebarFilter(); }
        });

        topBar.addPauseLiveFeedActionListener(e -> {
            boolean nextPaused = !liveFeedPanel.isPaused();
            liveFeedPanel.setPaused(nextPaused);
            topBar.setLiveFeedPaused(nextPaused);
        });
    }

    public static GUI getMyGui() {
        return myGui;
    }

    public TopBarPanel getTopBar() {
        return topBar;
    }

    public SidebarPanel getSidebar() {
        return sidebar;
    }

    public void setHosts(Set<String> hosts) {
        // Optional: logic to update sidebar hosts if needed, though IndexingEngine is used directly
    }

    public void refreshDisplay() {
        String selectedKey = sidebar.getSelectedKey();
        String currentPivot = topBar.getSelectedPivot();

        if (selectedKey == null) {
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
                logsToDisplay = IndexingEngine.TimeIndex
                        .getOrDefault(day, new java.util.concurrent.ConcurrentSkipListMap<>())
                        .getOrDefault(time.withSecond(0).withNano(0), new java.util.concurrent.CopyOnWriteArrayList<>());
            } else {
                LocalDate day = LocalDate.parse(selectedKey);
                logsToDisplay = IndexingEngine.getLogsByDay(day);
            }
        }

        selectedLogsPanel.renderLogs(logsToDisplay);
    }

    public void appendLiveLog(LogObject log) {
        liveFeedPanel.appendLiveLog(log);

        SwingUtilities.invokeLater(sidebar::applySidebarFilter);

        // If the current view matches the new log, we might want to refresh.
        String currentPivot = topBar.getSelectedPivot();
        String selectedKey = sidebar.getSelectedKey();

        if (selectedKey != null) {
            boolean shouldRefresh = false;
            if ("Hostnames".equals(currentPivot) && selectedKey.equals(log.getSource())) shouldRefresh = true;
            else if ("Category".equals(currentPivot) && selectedKey.equals(log.getCategory())) shouldRefresh = true;
            else if ("Severity".equals(currentPivot) && selectedKey.equals(log.getSeverity())) shouldRefresh = true;

            if (shouldRefresh) {
                SwingUtilities.invokeLater(this::refreshDisplay);
            }
        }
    }
}