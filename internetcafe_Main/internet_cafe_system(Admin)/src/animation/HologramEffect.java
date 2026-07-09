package animation;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public final class HologramEffect {

    private HologramEffect() {}

    public static void apply(Node target) {

        if (!(target instanceof Region)) return;

        Region panel = (Region) target;

        // Add hologram style class
        panel.getStyleClass().add("holo-panel");

        // Parent must be StackPane to overlay effects
        StackPane wrapper = new StackPane();
        wrapper.getChildren().add(panel);

        // Scanlines overlay
        Region scanlines = new Region();
        scanlines.getStyleClass().add("holo-scanlines");

        // Shimmer overlay
        Region shimmer = new Region();
        shimmer.getStyleClass().add("holo-shimmer");

        wrapper.getChildren().addAll(scanlines, shimmer);

        // Bind size to panel
        scanlines.prefWidthProperty().bind(panel.widthProperty());
        scanlines.prefHeightProperty().bind(panel.heightProperty());

        shimmer.prefWidthProperty().bind(panel.widthProperty());
        shimmer.prefHeightProperty().bind(panel.heightProperty());

        // ===== Scanlines animation =====
        Timeline scanMove = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(scanlines.translateYProperty(), 0)),
            new KeyFrame(Duration.seconds(3),
                new KeyValue(scanlines.translateYProperty(), 15))
        );
        scanMove.setAutoReverse(true);
        scanMove.setCycleCount(Animation.INDEFINITE);
        scanMove.play();

        // ===== Shimmer animation =====
        Timeline shimmerMove = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(shimmer.translateXProperty(), -200)),
            new KeyFrame(Duration.seconds(1.6),
                new KeyValue(shimmer.translateXProperty(), 200))
        );
        shimmerMove.setCycleCount(Animation.INDEFINITE);
        shimmerMove.play();
    }
}