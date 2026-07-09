package ui;

import java.awt.*;
import java.io.InputStream;

public class GameFont {

    private static Font pressStart;

    public static Font getPressStart(float size) {
        if (pressStart == null) {
            try {
                InputStream is = GameFont.class.getResourceAsStream(
                        "/fonts/PressStart2P-Regular.ttf"
                );
                pressStart = Font.createFont(Font.TRUETYPE_FONT, is);
            } catch (Exception e) {
                e.printStackTrace();
                pressStart = new Font("Monospaced", Font.BOLD, (int) size);
            }
        }
        return pressStart.deriveFont(size);
    }
}
