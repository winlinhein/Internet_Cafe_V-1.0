package ui;

import javax.swing.*;
import java.awt.*;

public class PixelCafeDialog {

    public static void show(String title, String message) {

        JDialog dialog = new JDialog();
        dialog.setTitle(title);
        dialog.setModal(true);
        dialog.setSize(360, 180);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);

        Color bgBlue = new Color(20, 30, 80);
        Color borderPink = new Color(255, 80, 160);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(bgBlue);
        panel.setBorder(BorderFactory.createLineBorder(borderPink, 4));

        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(GameFont.getPressStart(12f));

        // Button
        Button okButton = new Button("OK");
        okButton.setFont(GameFont.getPressStart(12f));
        okButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(bgBlue);
        buttonPanel.add(okButton);

        panel.add(label, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }
}
