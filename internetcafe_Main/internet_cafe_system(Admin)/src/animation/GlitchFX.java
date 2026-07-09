package animation;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Random;

/**
 * Universal glitch effects for JavaFX Nodes.
 *
 * Usage:
 *   GlitchFX.burst(node);
 *   GlitchFX.hover(node);  // glitch burst on hover
 *   Animation a = GlitchFX.loop(node, 900); // subtle loop
 *   GlitchFX.stop(node);
 *
 * Optional (stronger effect, requires node inside a Pane):
 *   GlitchFX.rgbSplit(node);
 */
public final class GlitchFX {

    private static final Random RNG = new Random();

    // keys for storing animations/effects per-node
    private static final String KEY_BURST = "px.glitch.burst";
    private static final String KEY_LOOP  = "px.glitch.loop";
    private static final String KEY_RGB   = "px.glitch.rgb";

    private GlitchFX() {}

    /** One-shot cyber glitch burst (quick jitter + tiny opacity flicker). */
    public static void burst(Node n) {
        if (n == null) return;

        stopBurst(n);

        final double baseX = n.getTranslateX();
        final double baseY = n.getTranslateY();
        final double baseOpacity = n.getOpacity();

        int steps = 10 + RNG.nextInt(6);        // 10..15
        int stepMs = 16;                        // ~60fps feel
        double maxX = 6.0;
        double maxY = 4.0;

        Timeline t = new Timeline();
        for (int i = 0; i < steps; i++) {
            double dx = (RNG.nextDouble() - 0.5) * 2.0 * maxX;
            double dy = (RNG.nextDouble() - 0.5) * 2.0 * maxY;

            // occasional "hard hit"
            if (RNG.nextDouble() < 0.18) {
                dx *= 1.8;
                dy *= 1.4;
            }

            double op = clamp(0.86 + RNG.nextDouble() * 0.18, 0.75, 1.0);

            t.getKeyFrames().add(new KeyFrame(Duration.millis(i * stepMs),
                    new KeyValue(n.translateXProperty(), baseX + dx, Interpolator.DISCRETE),
                    new KeyValue(n.translateYProperty(), baseY + dy, Interpolator.DISCRETE),
                    new KeyValue(n.opacityProperty(), op, Interpolator.DISCRETE)
            ));
        }

        // snap back
        t.getKeyFrames().add(new KeyFrame(Duration.millis(steps * stepMs + 80),
                new KeyValue(n.translateXProperty(), baseX, Interpolator.EASE_OUT),
                new KeyValue(n.translateYProperty(), baseY, Interpolator.EASE_OUT),
                new KeyValue(n.opacityProperty(), baseOpacity, Interpolator.EASE_OUT)
        ));

        t.setOnFinished(e -> {
            n.setTranslateX(baseX);
            n.setTranslateY(baseY);
            n.setOpacity(baseOpacity);
            n.getProperties().remove(KEY_BURST);
        });

        n.getProperties().put(KEY_BURST, t);
        t.play();
    }

    /** Burst glitch when mouse enters (keeps your existing handlers safe by chaining). */
    public static void hover(Node n) {
        if (n == null) return;

        // chain handlers safely
        var prevEnter = n.getOnMouseEntered();
        n.setOnMouseEntered(e -> {
            if (prevEnter != null) prevEnter.handle(e);
            burst(n);
        });
    }

    /**
     * Continuous subtle glitch loop.
     * @param intervalMs how often to trigger micro-bursts (recommend 700..1400)
     * @return the running Animation (PauseTransition loop)
     */
    public static Animation loop(Node n, long intervalMs) {
        if (n == null) return null;

        stopLoop(n);

        long iv = Math.max(200, intervalMs);

        PauseTransition p = new PauseTransition(Duration.millis(iv));
        p.setOnFinished(e -> {
            // tiny micro burst
            micro(n);
            // randomize next interval slightly
            p.setDuration(Duration.millis(iv * (0.75 + RNG.nextDouble() * 0.6)));
            p.playFromStart();
        });

        n.getProperties().put(KEY_LOOP, p);
        p.play();
        return p;
    }

    /** Stop all glitch effects (burst + loop + rgb split). */
    public static void stop(Node n) {
        if (n == null) return;
        stopBurst(n);
        stopLoop(n);
        stopRgbSplit(n);
    }

    // ---------- RGB SPLIT (optional stronger effect) ----------

    /**
     * RGB split "ghost clone" effect (strong).
     * Works best if node is inside a Pane (StackPane/AnchorPane/etc).
     * This creates 2 snapshot ImageViews that jitter briefly.
     */
    public static void rgbSplit(Node n) {
        if (n == null) return;

        // must have a Pane parent to insert overlays
        Parent parent = n.getParent();
        if (!(parent instanceof Pane)) {
            // fallback to normal burst if not supported
            burst(n);
            return;
        }
        Pane pane = (Pane) parent;

        stopRgbSplit(n);

        // snapshot on next pulse to ensure layout is done
        Platform.runLater(() -> {
            Bounds b = n.getBoundsInParent();
            if (b.getWidth() <= 2 || b.getHeight() <= 2) {
                burst(n);
                return;
            }

            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            WritableImage img = n.snapshot(sp, null);

            ImageView ghostA = new ImageView(img);
            ImageView ghostB = new ImageView(img);

            ghostA.setMouseTransparent(true);
            ghostB.setMouseTransparent(true);

            // Position ghosts to match node
            ghostA.setManaged(false);
            ghostB.setManaged(false);
            ghostA.setLayoutX(b.getMinX());
            ghostA.setLayoutY(b.getMinY());
            ghostB.setLayoutX(b.getMinX());
            ghostB.setLayoutY(b.getMinY());

            ghostA.setOpacity(0.55);
            ghostB.setOpacity(0.55);

            ghostA.getStyleClass().add("px-ghost-a");
            ghostB.getStyleClass().add("px-ghost-b");

            // Add above node (near its index)
            int idx = pane.getChildren().indexOf(n);
            if (idx < 0) idx = pane.getChildren().size();
            pane.getChildren().add(idx + 1, ghostA);
            pane.getChildren().add(idx + 2, ghostB);

            Timeline t = new Timeline();
            int steps = 9;

            for (int i = 0; i < steps; i++) {
                double ax = (RNG.nextDouble() - 0.5) * 10;
                double ay = (RNG.nextDouble() - 0.5) * 6;
                double bx = (RNG.nextDouble() - 0.5) * 10;
                double by = (RNG.nextDouble() - 0.5) * 6;

                t.getKeyFrames().add(new KeyFrame(Duration.millis(i * 18),
                        new KeyValue(ghostA.translateXProperty(), ax, Interpolator.DISCRETE),
                        new KeyValue(ghostA.translateYProperty(), ay, Interpolator.DISCRETE),
                        new KeyValue(ghostB.translateXProperty(), -bx, Interpolator.DISCRETE),
                        new KeyValue(ghostB.translateYProperty(), -by, Interpolator.DISCRETE),
                        new KeyValue(ghostA.opacityProperty(), 0.35 + RNG.nextDouble() * 0.35, Interpolator.DISCRETE),
                        new KeyValue(ghostB.opacityProperty(), 0.35 + RNG.nextDouble() * 0.35, Interpolator.DISCRETE)
                ));
            }

            t.getKeyFrames().add(new KeyFrame(Duration.millis(steps * 18 + 120),
                    new KeyValue(ghostA.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                    new KeyValue(ghostB.opacityProperty(), 0.0, Interpolator.EASE_OUT)
            ));

            t.setOnFinished(e -> {
                pane.getChildren().remove(ghostA);
                pane.getChildren().remove(ghostB);
                n.getProperties().remove(KEY_RGB);
            });

            n.getProperties().put(KEY_RGB, t);
            t.play();
        });
    }

    // ---------- internal helpers ----------

    private static void micro(Node n) {
        if (n == null) return;

        final double baseX = n.getTranslateX();
        final double baseY = n.getTranslateY();
        final double baseOpacity = n.getOpacity();

        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(0),
                        new KeyValue(n.translateXProperty(), baseX, Interpolator.DISCRETE),
                        new KeyValue(n.translateYProperty(), baseY, Interpolator.DISCRETE),
                        new KeyValue(n.opacityProperty(), baseOpacity, Interpolator.DISCRETE)
                ),
                new KeyFrame(Duration.millis(20),
                        new KeyValue(n.translateXProperty(), baseX + (RNG.nextDouble() - 0.5) * 4.0, Interpolator.DISCRETE),
                        new KeyValue(n.translateYProperty(), baseY + (RNG.nextDouble() - 0.5) * 2.5, Interpolator.DISCRETE),
                        new KeyValue(n.opacityProperty(), clamp(0.90 + RNG.nextDouble() * 0.10, 0.8, 1.0), Interpolator.DISCRETE)
                ),
                new KeyFrame(Duration.millis(90),
                        new KeyValue(n.translateXProperty(), baseX, Interpolator.EASE_OUT),
                        new KeyValue(n.translateYProperty(), baseY, Interpolator.EASE_OUT),
                        new KeyValue(n.opacityProperty(), baseOpacity, Interpolator.EASE_OUT)
                )
        );
        t.play();
    }

    private static void stopBurst(Node n) {
        Object a = n.getProperties().get(KEY_BURST);
        if (a instanceof Animation) ((Animation) a).stop();
        n.getProperties().remove(KEY_BURST);
    }

    private static void stopLoop(Node n) {
        Object a = n.getProperties().get(KEY_LOOP);
        if (a instanceof Animation) ((Animation) a).stop();
        n.getProperties().remove(KEY_LOOP);
    }

    private static void stopRgbSplit(Node n) {
        Object a = n.getProperties().get(KEY_RGB);
        if (a instanceof Animation) ((Animation) a).stop();
        n.getProperties().remove(KEY_RGB);
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}