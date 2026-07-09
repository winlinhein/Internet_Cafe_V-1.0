package animation;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.Random;

public class PixelFX {

    private static final Random R = new Random();
     private static final String GLITCH_LOCK = "glitchLock";
    private static final String GLITCH_TL   = "glitchTimeline";
    // ---------------------------
    // 1) Pixel Pop / Appear
    // ---------------------------
    public static Animation popIn(Node node) {
        node.setOpacity(0);
        node.setScaleX(0.6);
        node.setScaleY(0.6);

        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(node.opacityProperty(), 0, Interpolator.DISCRETE),
                new KeyValue(node.scaleXProperty(), 0.6, Interpolator.DISCRETE),
                new KeyValue(node.scaleYProperty(), 0.6, Interpolator.DISCRETE)
            ),
            new KeyFrame(Duration.millis(120),
                new KeyValue(node.opacityProperty(), 1, Interpolator.DISCRETE),
                new KeyValue(node.scaleXProperty(), 1.15, Interpolator.DISCRETE),
                new KeyValue(node.scaleYProperty(), 1.15, Interpolator.DISCRETE)
            ),
            new KeyFrame(Duration.millis(200),
                new KeyValue(node.scaleXProperty(), 0.95, Interpolator.DISCRETE),
                new KeyValue(node.scaleYProperty(), 0.95, Interpolator.DISCRETE)
            ),
            new KeyFrame(Duration.millis(280),
                new KeyValue(node.scaleXProperty(), 1.0, Interpolator.DISCRETE),
                new KeyValue(node.scaleYProperty(), 1.0, Interpolator.DISCRETE)
            )
        );
        return tl;
    }

    // ---------------------------
    // 2) Pixel Hover (on/off)
    // ---------------------------
    public static void installHover(Node node) {
        Timeline hoverOn = new Timeline(
            new KeyFrame(Duration.millis(90),
                new KeyValue(node.scaleXProperty(), 1.06, Interpolator.DISCRETE),
                new KeyValue(node.scaleYProperty(), 1.06, Interpolator.DISCRETE),
                new KeyValue(node.translateYProperty(), -1, Interpolator.DISCRETE)
            )
        );

        Timeline hoverOff = new Timeline(
            new KeyFrame(Duration.millis(90),
                new KeyValue(node.scaleXProperty(), 1.0, Interpolator.DISCRETE),
                new KeyValue(node.scaleYProperty(), 1.0, Interpolator.DISCRETE),
                new KeyValue(node.translateYProperty(), 0, Interpolator.DISCRETE)
            )
        );

        node.setOnMouseEntered(e -> {
            hoverOff.stop();
            hoverOn.playFromStart();
        });

        node.setOnMouseExited(e -> {
            hoverOn.stop();
            hoverOff.playFromStart();
        });
    }

    // ---------------------------
    // 3) Pixel Click Press
    // ---------------------------
    public static void installClickPress(Node node) {
        Timeline press = new Timeline(
            new KeyFrame(Duration.millis(60),
                new KeyValue(node.scaleXProperty(), 0.96, Interpolator.DISCRETE),
                new KeyValue(node.scaleYProperty(), 0.96, Interpolator.DISCRETE),
                new KeyValue(node.translateYProperty(), 1, Interpolator.DISCRETE)
            )
        );

        Timeline release = new Timeline(
            new KeyFrame(Duration.millis(60),
                new KeyValue(node.scaleXProperty(), 1.0, Interpolator.DISCRETE),
                new KeyValue(node.scaleYProperty(), 1.0, Interpolator.DISCRETE),
                new KeyValue(node.translateYProperty(), 0, Interpolator.DISCRETE)
            )
        );

        node.setOnMousePressed(e -> {
            release.stop();
            press.playFromStart();
        });

        node.setOnMouseReleased(e -> {
            press.stop();
            release.playFromStart();
        });
    }

    // ---------------------------
    // 4) Pixel Glitch (short burst)
    // ---------------------------
    public static Animation glitchBurst(Node node) {
        double baseX = node.getTranslateX();
        double baseY = node.getTranslateY();

        Timeline tl = new Timeline();
        int steps = 10; // more steps = more glitch

        for (int i = 0; i < steps; i++) {
            int dx = R.nextBoolean() ? 2 : -2;     // pixel jump
            int dy = R.nextBoolean() ? 1 : -1;

            tl.getKeyFrames().add(
                new KeyFrame(Duration.millis(i * 25),
                    new KeyValue(node.translateXProperty(), baseX + dx, Interpolator.DISCRETE),
                    new KeyValue(node.translateYProperty(), baseY + dy, Interpolator.DISCRETE),
                    new KeyValue(node.opacityProperty(), (i % 3 == 0) ? 0.75 : 1.0, Interpolator.DISCRETE)
                )
            );
        }

        tl.getKeyFrames().add(
            new KeyFrame(Duration.millis(steps * 25),
                new KeyValue(node.translateXProperty(), baseX, Interpolator.DISCRETE),
                new KeyValue(node.translateYProperty(), baseY, Interpolator.DISCRETE),
                new KeyValue(node.opacityProperty(), 1.0, Interpolator.DISCRETE)
            )
        );

        return tl;
    }

    // ---------------------------
    // 5) Install "Pixel UI pack" in one call
    // ---------------------------
    public static void installAll(Node node) {
        installHover(node);
        installClickPress(node);

        // small glitch when hover (optional)
        node.setOnMouseEntered(e -> {
            Animation g = glitchBurst(node);
            g.play();
        });
    }
    public static void glitchSwitch(Node from, Node to) {
        // prevent double-click spamming
        if (Boolean.TRUE.equals(from.getProperties().get(GLITCH_LOCK))) return;
        from.getProperties().put(GLITCH_LOCK, true);

        // stop previous glitch on "from" if any
        Timeline old = (Timeline) from.getProperties().get(GLITCH_TL);
        if (old != null) old.stop();

        // ensure "to" is ready but hidden behind
        to.setVisible(true);
        to.setManaged(true);
        to.setOpacity(0);

        // Remember base translate (so we always restore)
        double baseFromX = from.getTranslateX();
        double baseFromY = from.getTranslateY();
        double baseToX   = to.getTranslateX();
        double baseToY   = to.getTranslateY();

        Timeline glitchOut = new Timeline();

        int steps = 10;
        for (int i = 0; i < steps; i++) {
            int dx = R.nextBoolean() ? 3 : -3; // pixel jumps
            int dy = R.nextBoolean() ? 2 : -2;

            glitchOut.getKeyFrames().add(
                new KeyFrame(Duration.millis(i * 25),
                    new KeyValue(from.translateXProperty(), baseFromX + dx, Interpolator.DISCRETE),
                    new KeyValue(from.translateYProperty(), baseFromY + dy, Interpolator.DISCRETE),
                    new KeyValue(from.opacityProperty(), (i % 3 == 0) ? 0.55 : 1.0, Interpolator.DISCRETE)
                )
            );
        }

        // end: fade out "from"
        glitchOut.getKeyFrames().add(
            new KeyFrame(Duration.millis(steps * 25 + 60),
                new KeyValue(from.opacityProperty(), 0, Interpolator.DISCRETE),
                new KeyValue(from.translateXProperty(), baseFromX, Interpolator.DISCRETE),
                new KeyValue(from.translateYProperty(), baseFromY, Interpolator.DISCRETE)
            )
        );

        glitchOut.setOnFinished(e -> {
            // hide from
            from.setVisible(false);
            from.setManaged(false);
            from.setOpacity(1);
            from.setTranslateX(baseFromX);
            from.setTranslateY(baseFromY);

            // glitch-in "to"
            Timeline glitchIn = new Timeline();
            int inSteps = 6;

            for (int i = 0; i < inSteps; i++) {
                int dx = R.nextBoolean() ? 2 : -2;
                int dy = R.nextBoolean() ? 1 : -1;

                glitchIn.getKeyFrames().add(
                    new KeyFrame(Duration.millis(i * 25),
                        new KeyValue(to.opacityProperty(), 1.0, Interpolator.DISCRETE),
                        new KeyValue(to.translateXProperty(), baseToX + dx, Interpolator.DISCRETE),
                        new KeyValue(to.translateYProperty(), baseToY + dy, Interpolator.DISCRETE)
                    )
                );
            }

            glitchIn.getKeyFrames().add(
                new KeyFrame(Duration.millis(inSteps * 25 + 40),
                    new KeyValue(to.opacityProperty(), 1.0, Interpolator.DISCRETE),
                    new KeyValue(to.translateXProperty(), baseToX, Interpolator.DISCRETE),
                    new KeyValue(to.translateYProperty(), baseToY, Interpolator.DISCRETE)
                )
            );

            glitchIn.setOnFinished(ev -> from.getProperties().put(GLITCH_LOCK, false));
            glitchIn.playFromStart();
        });

        from.getProperties().put(GLITCH_TL, glitchOut);
        glitchOut.playFromStart();
    }
}
