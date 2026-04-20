package GUI;

import javax.swing.*;
import java.awt.*;

/**
 * A splash screen with a loading bar and status messages to show during application startup.
 */
public class SplashScreen extends JFrame {

    private final JProgressBar progressBar;
    private final JLabel statusLabel;

    /**
     * Constructs a new SplashScreen.
     */
    public SplashScreen() {
        // Remove window decorations
        setUndecorated(true);
        setTitle("Guard Dog NOC Bridge - Loading");
        setSize(500, 300);
        setLocationRelativeTo(null); // Center on screen
        setLayout(new BorderLayout());

        // Background color
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(25, 25, 25));
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(0, 150, 255), 2));

        // Content
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 40, 10, 40);

        JLabel titleLabel = new JLabel("Guard Dog NOC Bridge", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        gbc.gridy = 0;
        contentPanel.add(titleLabel, gbc);

        JLabel subtitleLabel = new JLabel("Initializing specialized log analysis stack...", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(180, 180, 180));
        gbc.gridy = 1;
        contentPanel.add(subtitleLabel, gbc);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(400, 20));
        progressBar.setForeground(new Color(0, 150, 255));
        progressBar.setBackground(new Color(45, 45, 45));
        progressBar.setBorderPainted(false);
        gbc.gridy = 2;
        gbc.insets = new Insets(30, 40, 5, 40);
        contentPanel.add(progressBar, gbc);

        statusLabel = new JLabel("Starting up...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        statusLabel.setForeground(new Color(150, 150, 150));
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 40, 10, 40);
        contentPanel.add(statusLabel, gbc);

        mainPanel.add(contentPanel, BorderLayout.CENTER);
        add(mainPanel);
    }

    /**
     * Updates the status message displayed on the splash screen.
     * @param status The status message.
     */
    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    /**
     * Sets the current progress of the loading bar.
     * @param value The progress value.
     */
    public void setProgress(int value) {
        SwingUtilities.invokeLater(() -> progressBar.setValue(value));
    }

    /**
     * Sets the maximum value for the loading bar.
     * @param max The maximum value.
     */
    public void setMax(int max) {
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(max));
    }

    /**
     * Sets whether the progress bar is indeterminate.
     * @param indeterminate True if indeterminate, false otherwise.
     */
    public void setIndeterminate(boolean indeterminate) {
        SwingUtilities.invokeLater(() -> progressBar.setIndeterminate(indeterminate));
    }
}
