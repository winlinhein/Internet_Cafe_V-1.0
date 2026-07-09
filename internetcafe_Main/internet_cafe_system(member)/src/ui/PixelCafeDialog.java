package ui;

import animation.PixelMotion;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class PixelCafeDialog {

    private PixelCafeDialog() {
    }

    public static void show(String title, String message) {
        Platform.runLater(() -> {
            Node anchor = findToastAnchor();
            if (anchor != null) {
                PixelMotion.toastGlitch(anchor, title, message, PixelMotion.ToastType.INFO);
            }
        });
    }

    private static Node findToastAnchor() {
        for (Window window : Window.getWindows()) {
            if (!(window instanceof Stage stage) || !stage.isShowing()) {
                continue;
            }
            Scene scene = stage.getScene();
            if (scene != null && scene.getRoot() != null) {
                return scene.getRoot();
            }
        }
        return null;
    }
}
