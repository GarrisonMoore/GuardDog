package GUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.event.DocumentListener;

public class TopBarPanel extends JPanel {
    private final JComboBox<String> pivotBox = new JComboBox<>(new String[]{"Hostnames", "Category", "Severity", "Time"});
    private final JButton pauseLiveFeedButton = new JButton("Pause Live Feed");

    public TopBarPanel() {
        setLayout(new BorderLayout(15, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        initComponents();
        setLiveFeedPaused(false);
    }

    private void initComponents() {
        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftControls.setOpaque(false);
        pivotBox.setPreferredSize(new Dimension(270, 45));
        pivotBox.setFont(GUIConstants.MAIN_FONT);
        pivotBox.putClientProperty("JComponent.outline", GUIConstants.ACCENT_COLOR);
        leftControls.add(pivotBox);

        pauseLiveFeedButton.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        pauseLiveFeedButton.setFont(GUIConstants.NAV_LABEL_FONT);
        pauseLiveFeedButton.putClientProperty("JButton.buttonType", "square"); 
        pauseLiveFeedButton.putClientProperty("JComponent.arc", 12);
        // Custom painting to have rounded corners only on top
        pauseLiveFeedButton.setUI(new com.formdev.flatlaf.ui.FlatButtonUI(false) {
            @Override
            protected void paintBackground(Graphics g, JComponent c) {
                if (((JButton)c).isContentAreaFilled()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(c.getBackground());
                        int arc = 12;
                        int width = c.getWidth();
                        int height = c.getHeight();
                        // Fill top rounded part
                        g2.fillRoundRect(0, 0, width, height, arc, arc);
                        // Fill bottom square part
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

        JPanel centerSearchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        centerSearchPanel.setOpaque(false);

        add(leftControls, BorderLayout.WEST);
    }



    public void addPauseLiveFeedActionListener(ActionListener listener) {
        pauseLiveFeedButton.addActionListener(listener);
    }

    public void setLiveFeedPaused(boolean paused) {
        pauseLiveFeedButton.setText(paused ? "RESUME LIVE FEED" : "PAUSE LIVE FEED");
        pauseLiveFeedButton.setBackground(paused ? GUIConstants.CRIT_COLOR : GUIConstants.SUCCESS_COLOR);
        pauseLiveFeedButton.setForeground(Color.WHITE);
    }

    public void addPivotActionListener(ActionListener listener) {
        pivotBox.addActionListener(listener);
    }

    public String getSelectedPivot() {
        return (String) pivotBox.getSelectedItem();
    }

    public JButton getPauseButton() {
        return pauseLiveFeedButton;
    }

}
