package GUI;

import SentryStack.LogObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;

public class LiveFeedPanel extends JPanel {
    private final DefaultTableModel liveTableModel = new DefaultTableModel(
            new Object[]{"Timestamp", "Host", "Severity", "Category", "PID", "Message"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable liveLogTable = new JTable(liveTableModel);
    private final JScrollPane liveTableScroll = new JScrollPane(liveLogTable);

    private final Deque<LogObject> logBuffer = new ArrayDeque<>();
    private boolean paused = false;

    public LiveFeedPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        initComponents();
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
        liveLogTable.add(SelectedLogsPanel.getSearchField());
        
        liveLogTable.setDefaultRenderer(Object.class, new LogSeverityRenderer());
        
        // Ensure "Message" column is wider
        liveLogTable.getColumnModel().getColumn(5).setPreferredWidth(600);
        
        // Add tooltips to show full message
        liveLogTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int row = liveLogTable.rowAtPoint(e.getPoint());
                int col = liveLogTable.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 5) {
                    Object value = liveLogTable.getValueAt(row, col);
                    if (value != null) {
                        liveLogTable.setToolTipText("<html><body style='width: 400px;'>" + value.toString() + "</body></html>");
                    }
                } else {
                    liveLogTable.setToolTipText(null);
                }
            }
        });

        add(liveTableScroll, BorderLayout.CENTER);
    }

    public void appendLiveLog(LogObject log) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendLiveLog(log));
            return;
        }

        if (paused) {
            logBuffer.addLast(log);
            return;
        }
        addLogRow(log);
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
        while (!logBuffer.isEmpty()) {
            appendLiveLog(logBuffer.removeFirst());
        }
    }

    public void addLogRow(LogObject log) {
        liveTableModel.addRow(new Object[]{
                formatTimestamp(log),
                log.getSource(),
                log.getSeverity(),
                log.getCategory(),
                log.getPid(),
                log.getMessage()
        });
        if (liveTableModel.getRowCount() > 500) {
            liveTableModel.removeRow(0);
        }
        int lastRow = liveTableModel.getRowCount() - 1;
        if (lastRow >= 0) {
            liveLogTable.scrollRectToVisible(liveLogTable.getCellRect(lastRow, 0, true));
        }
    }

    public JComponent getScroll() {
        return liveTableScroll;
    }
}
