package GUI;

import SentryStack.LogObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;

public class LiveFeedPanel extends JPanel {
    // 1. Update the Model to AbstractTableModel (High Performance)
    private final java.util.List<Object[]> currentTableData = new java.util.ArrayList<>();
    private final String[] columnNames = {"Timestamp", "Host", "Severity", "Category", "PID", "Message"};

    private final javax.swing.table.AbstractTableModel liveTableModel = new javax.swing.table.AbstractTableModel() {
        @Override public int getRowCount() { return currentTableData.size(); }
        @Override public int getColumnCount() { return columnNames.length; }
        @Override public String getColumnName(int col) { return columnNames[col]; }
        @Override public Object getValueAt(int row, int col) { return currentTableData.get(row)[col]; }
    };

    private final JTable liveLogTable = new JTable(liveTableModel) {
        @Override
        public String getToolTipText(java.awt.event.MouseEvent e) {
            java.awt.Point p = e.getPoint();
            int row = rowAtPoint(p);
            int col = columnAtPoint(p);

            if (row >= 0 && col == 5) {
                Object value = getValueAt(row, col);
                if (value != null) {
                    return "<html><body style='width: 400px;'>" + value.toString() + "</body></html>";
                }
            }
            return super.getToolTipText(e);
        }
    };

    // CPU SAVER: Static formatter used by all logs
    private static final java.time.format.DateTimeFormatter DATE_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JScrollPane liveTableScroll = new JScrollPane(liveLogTable);

    // Use a synchronized LinkedList instead of CopyOnWrite to stop CPU churning
    private final java.util.List<LogObject> allLiveLogs = java.util.Collections.synchronizedList(new java.util.LinkedList<>());
    private final Deque<LogObject> logBuffer = new ArrayDeque<>();
    // NEW: A concurrent queue to hold incoming logs until the UI is ready to draw them
    private final java.util.concurrent.ConcurrentLinkedQueue<LogObject> pendingLogs = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private boolean paused = false;

    // Tracks the last known state so we don't do useless redraws
    private int lastLogCount = -1;
    private String lastFilter = "";

    public LiveFeedPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        initComponents();

        // NEW: Batch processor timer (runs every 250ms)
        new javax.swing.Timer(250, e -> processPendingLogs()).start();
    }

    private void initComponents() {
        liveTableScroll.setBorder(BorderFactory.createLineBorder(GUIConstants.BORDER_COLOR, 1));
        liveTableScroll.putClientProperty("JComponent.arc", 12);
        liveTableScroll.getVerticalScrollBar().setUnitIncrement(16);

        liveLogTable.setBackground(GUIConstants.LOG_BG);
        liveLogTable.setForeground(Color.WHITE);
        liveLogTable.setGridColor(GUIConstants.BORDER_COLOR);
        liveLogTable.getTableHeader().setBackground(GUIConstants.PANEL_BG);
        liveLogTable.getTableHeader().setForeground(Color.WHITE);
        liveLogTable.setSelectionBackground(new Color(30, 80, 150));
        liveLogTable.setSelectionForeground(Color.WHITE);
        liveLogTable.setFont(GUIConstants.MAIN_FONT);

        liveLogTable.setDefaultRenderer(Object.class, new LogSeverityRenderer());

        // Ensure "Message" column is wider
        liveLogTable.getColumnModel().getColumn(5).setPreferredWidth(600);
    }

    public void appendLiveLog(LogObject log) {
        // Just add to our data structures instantly. No UI blocking!
        allLiveLogs.add(log);
        if (allLiveLogs.size() > 500) {
            allLiveLogs.remove(0);
        }
        pendingLogs.add(log);
    }

    private void processPendingLogs() {
        if (pendingLogs.isEmpty()) return;

        boolean addedToTable = false;

        // 1. Process the incoming queue
        while (!pendingLogs.isEmpty()) {
            LogObject log = pendingLogs.poll();

            if (paused) {
                logBuffer.addLast(log);
                continue;
            }

            if (matchesFilter(log)) {
                // Instead of .addRow, we add to our internal List
                currentTableData.add(new Object[]{
                        formatTimestamp(log),
                        log.getSource(),
                        log.getSeverity(),
                        log.getCategory(),
                        log.getPid(),
                        log.getMessage()
                });
                addedToTable = true;
            }
        }

        // 2. Clean up the list if it gets too long (Trimming)
        while (currentTableData.size() > 500) {
            currentTableData.remove(0);
        }

        // 3. Notify the UI to update the screen
        if (addedToTable) {
            // This fires exactly ONE event for the whole batch
            liveTableModel.fireTableDataChanged();

            // Scroll to the bottom safely on the UI thread
            int lastRow = currentTableData.size() - 1;
            if (lastRow >= 0) {
                liveLogTable.scrollRectToVisible(liveLogTable.getCellRect(lastRow, 0, true));
            }
        }
    }

    private boolean matchesFilter(LogObject log) {
        String filter = SelectedLogsPanel.getSearchField().getText().trim().toLowerCase();
        if (filter.isEmpty()) return true;

        return log.getMessage().toLowerCase().contains(filter) ||
                log.getSource().toLowerCase().contains(filter) ||
                log.getCategory().toLowerCase().contains(filter) ||
                log.getSeverity().toLowerCase().contains(filter);
    }

    // 3. Update refreshDisplay with the Short-Circuit and Batch Update
    public void refreshDisplay() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshDisplay);
            return;
        }

        // Clear the current display list
        currentTableData.clear();

        String filter = SelectedLogsPanel.getSearchField().getText().trim().toLowerCase();

        // CPU SAVER: If nothing changed, do absolutely zero work
        if (allLiveLogs.size() == lastLogCount && filter.equals(lastFilter)) {
            return;
        }

        lastLogCount = allLiveLogs.size();
        lastFilter = filter;

        // Build the new view in memory
        java.util.List<Object[]> newData = new java.util.ArrayList<>();
        synchronized(allLiveLogs) {
            for (LogObject log : allLiveLogs) {
                if (matchesFilter(log)) {
                    newData.add(new Object[]{
                            formatTimestamp(log), // Uses your static formatter
                            log.getSource(),
                            log.getSeverity(),
                            log.getCategory(),
                            log.getPid(),
                            log.getMessage()
                    });
                }
            }
        }

        // Hot-swap and fire exactly ONE event
        currentTableData.clear();
        currentTableData.addAll(newData);
        liveTableModel.fireTableDataChanged();
    }

    private void addLogRowToTable(LogObject log) {
        // 1. Add the new data to your internal list instead of the model
        currentTableData.add(new Object[]{
                formatTimestamp(log),
                log.getSource(),
                log.getSeverity(),
                log.getCategory(),
                log.getPid(),
                log.getMessage()
        });

        // 2. Enforce the 500-row limit on the list itself
        if (currentTableData.size() > 500) {
            currentTableData.remove(0);
        }

        // 3. Notify the AbstractTableModel that it needs to refresh the view
        liveTableModel.fireTableDataChanged();

        // 4. Perform the auto-scroll
        int lastRow = currentTableData.size() - 1;
        if (lastRow >= 0) {
            liveLogTable.scrollRectToVisible(liveLogTable.getCellRect(lastRow, 0, true));
        }
    }

    private String formatTimestamp(LogObject log) {
        return java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(log.getTimestamp()),
                java.time.ZoneId.systemDefault()
        ).format(DATE_FORMATTER);
    }

    public void setPaused(boolean paused) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setPaused(paused));
            return;
        }

        this.paused = paused;
        if (!paused) {
            flushBuffer();
        }
    }

    public boolean isPaused() {
        return paused;
    }

    private void flushBuffer() {
        boolean added = false;
        while (!logBuffer.isEmpty()) {
            LogObject log = logBuffer.removeFirst();
            if (matchesFilter(log)) {
                // Manually add to list without triggering a UI refresh yet
                currentTableData.add(new Object[]{
                        formatTimestamp(log),
                        log.getSource(),
                        log.getSeverity(),
                        log.getCategory(),
                        log.getPid(),
                        log.getMessage()
                });
                added = true;
            }
        }

        if (added) {
            // Trim the list if needed
            while (currentTableData.size() > 500) {
                currentTableData.remove(0);
            }

            // Refresh UI once for the whole batch
            liveTableModel.fireTableDataChanged();

            int lastRow = currentTableData.size() - 1;
            if (lastRow >= 0) {
                liveLogTable.scrollRectToVisible(liveLogTable.getCellRect(lastRow, 0, true));
            }
        }
    }

    public JComponent getScroll() {
        return liveTableScroll;
    }
}
