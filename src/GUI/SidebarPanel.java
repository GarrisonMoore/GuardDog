package GUI;

import SentryStack.IndexingEngine;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.time.LocalTime;

public class SidebarPanel extends JPanel {
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> hostList = new JList<>(listModel);
    private final JComboBox<String> pivotBox = new JComboBox<>(new String[]{"Hostnames", "Category", "Severity", "Time"});
    private final JTextField searchField = new JTextField();
    private final JButton backButton = new JButton("↩");
    private final GUI parent;

    private enum BrowseMode { DAYS, TIMES, OTHER }
    private BrowseMode browseMode = BrowseMode.OTHER;
    private LocalDate selectedDay = null;

    public SidebarPanel(GUI parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(300, 0));
        initComponents();
        setupListeners();
        layoutComponents();
        loadDays();
    }

    private void initComponents() {
        hostList.setFont(GUIConstants.MAIN_FONT);
        hostList.setBackground(GUIConstants.LIST_BG);
        hostList.setFixedCellHeight(40);
        hostList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        hostList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
                if (isSelected) {
                    label.setBackground(new Color(30, 80, 150));
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(GUIConstants.LIST_BG);
                    label.setForeground(new Color(200, 200, 200));
                }
                return label;
            }
        });

        searchField.setPreferredSize(new Dimension(270, 30));
        searchField.setFont(GUIConstants.MAIN_FONT);
        searchField.putClientProperty("JTextField.placeholderText", "Search logs...");
        searchField.putClientProperty("JComponent.outline", GUIConstants.ACCENT_COLOR);

        pivotBox.setPreferredSize(new Dimension(270, 35));
        pivotBox.setFont(GUIConstants.MAIN_FONT);
        pivotBox.putClientProperty("JComponent.outline", GUIConstants.ACCENT_COLOR);

        backButton.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 14));
        backButton.setBackground(GUIConstants.LIST_BG);
        backButton.putClientProperty("JComponent.outline", GUIConstants.ACCENT_COLOR);
        backButton.setPreferredSize(new Dimension(25, 25));
        backButton.setFocusPainted(false);
        backButton.setEnabled(false);
    }

    private void setupListeners() {
        hostList.addListSelectionListener(e -> {
            // Added a null check so it doesn't fire when the list is clearing/rebuilding
            if (!e.getValueIsAdjusting() && hostList.getSelectedValue() != null) {
                parent.getSelectedLogsPanel().clearMemory();
                parent.refreshDisplay();
            }
        });

        backButton.addActionListener(e -> {
            if ("Time".equals(getSelectedPivot())) {
                if (browseMode == BrowseMode.TIMES) {
                    loadDays();
                    selectedDay = null;
                    backButton.setEnabled(false);
                    parent.refreshDisplay();
                }
            }
        });

        // Sidebar search: re-filter list on every change
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applySidebarFilter(); }
            public void removeUpdate(DocumentEvent e) { applySidebarFilter(); }
            public void changedUpdate(DocumentEvent e) { applySidebarFilter(); }
        });

        // Pivot changes: update list contents + refresh logs
        pivotBox.addActionListener(e -> {
            String selected = (String) pivotBox.getSelectedItem();
            onPivotChanged(selected);
        });
    }

    private void layoutComponents() {
        JPanel navHeader = new JPanel(new BorderLayout(15, 15));
        navHeader.setOpaque(false);
        navHeader.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        
        JLabel navLabel = new JLabel("NAVIGATION");
        navLabel.setFont(GUIConstants.NAV_LABEL_FONT);
        navLabel.setForeground(new Color(150, 150, 150));
        navLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Search row: backButton + searchField
        JPanel searchRow = new JPanel(new BorderLayout(5, 0));
        searchRow.setOpaque(false);
        searchRow.add(backButton, BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.setMaximumSize(new Dimension(270, 40));

        // Stack: NAVIGATION (top), pivot (middle), search + back (bottom)
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        navLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        pivotBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        searchRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        controls.add(navLabel);
        controls.add(Box.createVerticalStrut(12));
        controls.add(pivotBox);
        controls.add(Box.createVerticalStrut(12));
        controls.add(searchRow);
        controls.add(Box.createVerticalStrut(5));

        navHeader.add(controls, BorderLayout.NORTH);
        
        JScrollPane listScroll = new JScrollPane(hostList);
        listScroll.setBorder(BorderFactory.createLineBorder(GUIConstants.BORDER_COLOR, 1));
        listScroll.putClientProperty("JComponent.arc", 12);
        listScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel listContainer = new JPanel(new BorderLayout());
        listContainer.setOpaque(false);
        listContainer.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 10));
        listContainer.add(navHeader, BorderLayout.NORTH);
        listContainer.add(listScroll, BorderLayout.CENTER);

        add(listContainer, BorderLayout.CENTER);
    }

    public void applySidebarFilter() {
        String query = getSearchText().toLowerCase();
        String currentPivot = getSelectedPivot();
        String currentlySelected = hostList.getSelectedValue();

        listModel.clear();

        if ("Time".equals(currentPivot)) {
            if (browseMode == BrowseMode.DAYS) {
                for (LocalDate day : IndexingEngine.getAvailableDays()) {
                    if (day.toString().contains(query)) listModel.addElement(day.toString());
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
            String[] categories = {"SECURITY & ERRORS", "WARNINGS", "AUTH & ACCESS", "SYSTEM & SERVICES", "POLICY & AUDIT", "NETWORK", "UNCATEGORIZED"};
            for (String cat : categories) {
                if (cat.toLowerCase().contains(query)) listModel.addElement(cat);
            }
        } else if ("Severity".equals(currentPivot)) {
            String[] severities = {"INFO", "WARN", "CRIT"};
            for (String sev : severities) {
                if (sev.toLowerCase().contains(query)) listModel.addElement(sev);
            }
        }

        if (currentlySelected != null && listModel.contains(currentlySelected)) {
            hostList.setSelectedValue(currentlySelected, true);
        }
    }

    public void onPivotChanged(String selected) {
        listModel.clear();
        selectedDay = null;
        browseMode = BrowseMode.OTHER;

        if ("Hostnames".equals(selected)) {
            for (String host : IndexingEngine.getHostKeys()) listModel.addElement(host);
        } else if ("Category".equals(selected)) {
            String[] categories = {"SECURITY & ERRORS", "WARNINGS", "AUTH & ACCESS", "SYSTEM & SERVICES", "POLICY & AUDIT", "NETWORK", "UNCATEGORIZED"};
            for (String cat : categories) listModel.addElement(cat);
        } else if ("Severity".equals(selected)) {
            listModel.addElement("INFO");
            listModel.addElement("WARN");
            listModel.addElement("CRIT");
        } else if ("Time".equals(selected)) {
            loadDays();
        }
        backButton.setEnabled(false);
        parent.refreshDisplay();
    }

    public void loadDays() {
        browseMode = BrowseMode.DAYS;
        listModel.clear();
        for (LocalDate day : IndexingEngine.getAvailableDays()) {
            listModel.addElement(day.toString());
        }
    }

    public void loadTimesForDay(LocalDate day) {
        browseMode = BrowseMode.TIMES;
        selectedDay = day;
        listModel.clear();
        for (LocalTime time : IndexingEngine.getAvailableTimes(day)) {
            listModel.addElement(time.toString().substring(0, 5));
        }
        backButton.setEnabled(true);
    }

    public void addPivotActionListener(ActionListener listener) {
        pivotBox.addActionListener(listener);
    }

    public String getSelectedPivot() {
        return (String) pivotBox.getSelectedItem();
    }

    public void addSearchDocumentListener(DocumentListener listener) {
        searchField.getDocument().addDocumentListener(listener);
    }

    public String getSearchText() {
        return searchField.getText().trim();
    }


    public String getSelectedKey() {
        return hostList.getSelectedValue();
    }

    public String getPivot() {
        return (String) pivotBox.getSelectedItem();
    }

    public LocalDate getSelectedDay() {
        return selectedDay;
    }

    public boolean isTimesMode() {
        return browseMode == BrowseMode.TIMES;
    }

    public void clearSelection() {
        hostList.clearSelection();
    }
}
