package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Button extends JButton {

    private final Color normalColor = new Color(255, 80, 160);
    private final Color hoverColor  = new Color(255, 120, 190);
    private final Color pressedColor = new Color(200, 60, 130);

    public Button(String text) {
        super(text);

        setFont(new Font("Consolas", Font.BOLD, 14));
        setForeground(Color.WHITE);
        setBackground(normalColor);

        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(true);

        // Pixel edges
        setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));

        // Mouse effects
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setBackground(hoverColor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(normalColor);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                setBackground(pressedColor);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                setBackground(hoverColor);
            }
        });
    }
}
