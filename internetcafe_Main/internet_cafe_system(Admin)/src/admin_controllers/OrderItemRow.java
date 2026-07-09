package admin_controllers;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class OrderItemRow {

    @FXML private HBox itemRoot;
    @FXML private Region dot;
    @FXML private Label titleLabel;
    @FXML private Label timeLabel;
    @FXML private Label tagLabel;
    @FXML private VBox textBox;
    @FXML private Button dismissBtn;

    private Runnable onDismiss;
    private Runnable onClick;
    private TranslateTransition hoverSlide;

    @FXML
    public void initialize() {
        installHoverMotion();
        itemRoot.setOnMouseClicked(this::handleRowClick);
        itemRoot.setStyle("-fx-cursor: hand;");
    }

    public void setSummaryData(String memberName, String pcName, int itemCount,
                               double totalAmount, String orderStatus,
                               Runnable onOpenDetail, Runnable onDismiss) {
        this.onClick = onOpenDetail;
        this.onDismiss = onDismiss;

        titleLabel.setText(memberName + " @ " + pcName);
        timeLabel.setText(String.format("%d item%s - P %.2f",
                itemCount, itemCount == 1 ? "" : "s", totalAmount));

        String normalizedStatus = normalizeStatus(orderStatus);
        tagLabel.setText(prettyStatus(normalizedStatus));

        tagLabel.getStyleClass().removeAll("notify-item-tag-info", "notify-item-tag-warn", "notify-item-tag-ok");
        dot.getStyleClass().removeAll("notify-item-dot-info", "notify-item-dot-warn", "notify-item-dot-ok");

        if ("completed".equals(normalizedStatus)) {
            tagLabel.getStyleClass().add("notify-item-tag-ok");
            dot.getStyleClass().add("notify-item-dot-ok");
            if (dismissBtn != null) {
                dismissBtn.setDisable(false);
                dismissBtn.setTooltip(new Tooltip("Dismiss this notification"));
            }
        } else {
            tagLabel.getStyleClass().add("notify-item-tag-warn");
            dot.getStyleClass().add("notify-item-dot-warn");
            if (dismissBtn != null) {
                dismissBtn.setDisable(true);
                dismissBtn.setTooltip(new Tooltip("Cannot dismiss pending orders"));
            }
        }
    }

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null) return "pending";
        String status = rawStatus.trim().toLowerCase();
        if (status.isEmpty()) return "pending";
        if (status.equals("completed") || status.equals("complete") || status.equals("done")) {
            return "completed";
        }
        return "pending";
    }

    private String prettyStatus(String normalizedStatus) {
        return "completed".equals(normalizedStatus) ? "COMPLETED" : "PENDING";
    }

    private void handleRowClick(MouseEvent event) {
        if (event.getTarget() == dismissBtn) return;
        if (onClick != null) onClick.run();
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
            if (onDismiss != null) onDismiss.run();
        });

        fade.play();
        slide.play();
        shrink.play();
    }

    private void installHoverMotion() {
        if (itemRoot == null) return;
        itemRoot.setScaleX(1.0);
        itemRoot.setScaleY(1.0);
        itemRoot.setTranslateX(0.0);
        itemRoot.setOnMouseEntered(e -> animateHover(true));
        itemRoot.setOnMouseExited(e -> animateHover(false));
    }

    private void animateHover(boolean hovered) {
        if (itemRoot == null) return;
        if (hoverSlide != null) hoverSlide.stop();
        hoverSlide = new TranslateTransition(Duration.millis(130), itemRoot);
        hoverSlide.setToX(hovered ? 4.0 : 0.0);
        hoverSlide.setInterpolator(Interpolator.EASE_BOTH);
        hoverSlide.play();
    }

    public HBox getItemRoot() { return itemRoot; }
}