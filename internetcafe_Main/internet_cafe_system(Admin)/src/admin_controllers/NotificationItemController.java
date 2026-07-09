package admin_controllers;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class NotificationItemController {

    private TranslateTransition hoverSlide;

    @FXML private HBox itemRoot;
    @FXML private Region dot;
    @FXML private Label titleLabel;
    @FXML private Label timeLabel;
    @FXML private Label tagLabel;
    @FXML private VBox textBox;
    @FXML private Button dismissBtn;

    private Runnable onDismiss;

    @FXML
    public void initialize() {
        installHoverMotion();
    }

    public void setData(String message, String timeText, String tagText, Runnable onDismiss) {
        this.onDismiss = onDismiss;
        titleLabel.setText(message == null ? "" : message);
        timeLabel.setText(timeText == null || timeText.trim().isEmpty() ? "JUST NOW" : timeText.trim());

        String safeTag = tagText == null ? "LIVE" : tagText.trim();
        if (safeTag.isEmpty()) {
            safeTag = "LIVE";
        }
        tagLabel.setText(safeTag.toUpperCase());

        tagLabel.getStyleClass().removeAll("notify-item-tag-info", "notify-item-tag-warn", "notify-item-tag-ok");
        dot.getStyleClass().removeAll("notify-item-dot-info", "notify-item-dot-warn", "notify-item-dot-ok");

        String normalized = safeTag.toLowerCase();
        if (normalized.contains("warn") || normalized.contains("alert") || normalized.contains("due") || normalized.contains("urgent")) {
            tagLabel.getStyleClass().add("notify-item-tag-warn");
            dot.getStyleClass().add("notify-item-dot-warn");
        } else if (normalized.contains("ok") || normalized.contains("done") || normalized.contains("success") || normalized.contains("new")) {
            tagLabel.getStyleClass().add("notify-item-tag-ok");
            dot.getStyleClass().add("notify-item-dot-ok");
        } else {
            tagLabel.getStyleClass().add("notify-item-tag-info");
            dot.getStyleClass().add("notify-item-dot-info");
        }
    }

    public HBox getItemRoot() {
        return itemRoot;
    }

    @FXML
    private void dismiss() {
        if (itemRoot == null) {
            if (onDismiss != null) onDismiss.run();
            return;
        }

        FadeTransition fade = new FadeTransition(Duration.millis(150), itemRoot);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(170), itemRoot);
        slide.setFromX(0.0);
        slide.setToX(24.0);
        slide.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(170), itemRoot);
        shrink.setToX(0.96);
        shrink.setToY(0.96);
        shrink.setInterpolator(Interpolator.EASE_BOTH);

        fade.setOnFinished(e -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        fade.play();
        slide.play();
        shrink.play();
    }

    private void installHoverMotion() {
        if (itemRoot == null) {
            return;
        }

        itemRoot.setScaleX(1.0);
        itemRoot.setScaleY(1.0);
        itemRoot.setTranslateX(0.0);

        itemRoot.setOnMouseEntered(e -> animateHover(true));
        itemRoot.setOnMouseExited(e -> animateHover(false));
    }

    private void animateHover(boolean hovered) {
        if (itemRoot == null) {
            return;
        }

        if (hoverSlide != null) {
            hoverSlide.stop();
        }

        hoverSlide = new TranslateTransition(Duration.millis(130), itemRoot);
        hoverSlide.setToX(hovered ? 4.0 : 0.0);
        hoverSlide.setInterpolator(Interpolator.EASE_BOTH);
        hoverSlide.play();
    }
}