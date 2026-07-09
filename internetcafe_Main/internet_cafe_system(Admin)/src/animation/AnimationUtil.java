/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package animation;

import java.util.HashMap;
import java.util.Random;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 *
 * @author Win Lin Hein
 */
public class AnimationUtil {
     public static void animateAppear(Node node, double delaySeconds) {
        // Initial state
        node.setOpacity(0);
        node.setTranslateY(40);
        node.setScaleX(0.98);
        node.setScaleY(0.98);

        // Fade in
        FadeTransition fade = new FadeTransition(Duration.seconds(1.0), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_BOTH);

        // Slide up
        TranslateTransition slide = new TranslateTransition(Duration.seconds(1.0), node);
        slide.setFromY(40);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_BOTH);

        // Subtle scale effect (pop-in)
        ScaleTransition scale = new ScaleTransition(Duration.seconds(1.0), node);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);

        // Combine all transitions
        ParallelTransition animation = new ParallelTransition(fade, slide, scale);

        // Optional delay for staggered appearance
        animation.setDelay(Duration.seconds(delaySeconds));

        animation.play();
    }
    
    public static void fadeSwitch(Node hide, Node show) {
        // Ensure both nodes are visible at start
        hide.setVisible(true);
        show.setVisible(false);

        // Fade out the current node
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), hide);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);

        // Slight scale down for subtle depth effect
        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(300), hide);
        scaleOut.setToX(0.98);
        scaleOut.setToY(0.98);
        scaleOut.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition hideAnim = new ParallelTransition(fadeOut, scaleOut);

        hideAnim.setOnFinished(e -> {
            hide.setVisible(false);
            hide.setOpacity(1);    // reset for future use
            hide.setScaleX(1);
            hide.setScaleY(1);

            // Prepare the new node
            show.setOpacity(0);
            show.setScaleX(1.02);  // subtle pop-in effect
            show.setScaleY(1.02);
            show.setVisible(true);

            // Fade in with slight scale normalization
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), show);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.setInterpolator(Interpolator.EASE_BOTH);

            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(300), show);
            scaleIn.setToX(1);
            scaleIn.setToY(1);
            scaleIn.setInterpolator(Interpolator.EASE_BOTH);
            
            TranslateTransition slide = new TranslateTransition(Duration.millis(300), show);
            slide.setFromY(10);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition showAnim = new ParallelTransition(fadeIn, scaleIn, slide);
            showAnim.play();
        });

        hideAnim.play();
    }
     public static void smoothBounce(Node node) {

    ScaleTransition grow = new ScaleTransition(Duration.millis(180), node);
    grow.setToX(1.08);
    grow.setToY(1.08);
    grow.setInterpolator(Interpolator.EASE_OUT);

    ScaleTransition settle = new ScaleTransition(Duration.millis(180), node);
    settle.setToX(1);
    settle.setToY(1);
    settle.setInterpolator(Interpolator.EASE_OUT);

    SequentialTransition bounce = new SequentialTransition(grow, settle);
    bounce.play();
}
     public static void smoothHover(Node node) {

    ScaleTransition scaleUp = new ScaleTransition(Duration.millis(220), node);
    scaleUp.setToX(1.04);
    scaleUp.setToY(1.04);
    scaleUp.setInterpolator(Interpolator.EASE_OUT);

    ScaleTransition scaleDown = new ScaleTransition(Duration.millis(220), node);
    scaleDown.setToX(1);
    scaleDown.setToY(1);
    scaleDown.setInterpolator(Interpolator.EASE_OUT);

    node.setOnMouseEntered(e -> scaleUp.playFromStart());
    node.setOnMouseExited(e -> scaleDown.playFromStart());
    node.setCache(true);
}
     public static void smoothSlideIn(Node node) {

    node.setOpacity(0);
    node.setTranslateY(20);

    FadeTransition fade = new FadeTransition(Duration.millis(350), node);
    fade.setToValue(1);
    fade.setInterpolator(Interpolator.EASE_OUT);

    TranslateTransition slide = new TranslateTransition(Duration.millis(350), node);
    slide.setToY(0);
    slide.setInterpolator(Interpolator.EASE_OUT);

    ParallelTransition pt = new ParallelTransition(fade, slide);
    pt.play();
}
     public static void smoothClick(Node node) {

    ScaleTransition press = new ScaleTransition(Duration.millis(100), node);
    press.setToX(0.96);
    press.setToY(0.96);
    press.setInterpolator(Interpolator.EASE_OUT);

    ScaleTransition release = new ScaleTransition(Duration.millis(150), node);
    release.setToX(1);
    release.setToY(1);
    release.setInterpolator(Interpolator.EASE_OUT);

    SequentialTransition seq = new SequentialTransition(press, release);
    seq.play();
}
     public static void smoothFadeSwitch(Node node) {

    FadeTransition fadeOut = new FadeTransition(Duration.millis(200), node);
    fadeOut.setToValue(0);
    fadeOut.setInterpolator(Interpolator.EASE_OUT);

    FadeTransition fadeIn = new FadeTransition(Duration.millis(300), node);
    fadeIn.setToValue(1);
    fadeIn.setInterpolator(Interpolator.EASE_OUT);

    SequentialTransition seq = new SequentialTransition(fadeOut, fadeIn);
    seq.play();
}
public static void smoothFloat(Node node) {

    TranslateTransition floatAnim = new TranslateTransition(Duration.seconds(1), node);
    floatAnim.setFromY(0);
    floatAnim.setToY(-10);
    floatAnim.setInterpolator(Interpolator.EASE_BOTH);
    floatAnim.setCycleCount(Animation.INDEFINITE);
    floatAnim.setAutoReverse(true);

    floatAnim.play();
}
 public class SmoothNodeHelper {

    // Stores the last known state for multiple nodes
    private static final HashMap<Node, NodeState> nodeStates = new HashMap<>();

    // Call this to show a node with smooth appear animation
    public static void show(Node node, double delaySeconds) {
        resetNode(node);

        node.setVisible(true);

        FadeTransition fade = new FadeTransition(Duration.seconds(1.0), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition slide = new TranslateTransition(Duration.seconds(1.0), node);
        slide.setFromY(20);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale = new ScaleTransition(Duration.seconds(1.0), node);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition appear = new ParallelTransition(fade, slide, scale);
        appear.setDelay(Duration.seconds(delaySeconds));
        appear.play();

        // Store current state
        nodeStates.put(node, new NodeState(node));
    }

    // Call this to hide a node smoothly
    public static void hide(Node node) {
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(0.5), node);
        fadeOut.setToValue(0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition slideDown = new TranslateTransition(Duration.seconds(0.5), node);
        slideDown.setToY(20);
        slideDown.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition shrink = new ScaleTransition(Duration.seconds(0.5), node);
        shrink.setToX(0.98);
        shrink.setToY(0.98);
        shrink.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition hideAnim = new ParallelTransition(fadeOut, slideDown, shrink);
        hideAnim.setOnFinished(e -> node.setVisible(false));
        hideAnim.play();
    }

    // Resets node transforms to last known state
    private static void resetNode(Node node) {
        NodeState state = nodeStates.get(node);
        if (state != null) {
            node.setTranslateX(state.translateX);
            node.setTranslateY(state.translateY);
            node.setScaleX(state.scaleX);
            node.setScaleY(state.scaleY);
            node.setOpacity(0);
        } else {
            node.setTranslateX(0);
            node.setTranslateY(20);
            node.setScaleX(0.98);
            node.setScaleY(0.98);
            node.setOpacity(0);
        }
    }

    // Internal class to remember node state
    private static class NodeState {
        double translateX, translateY, scaleX, scaleY;

        NodeState(Node node) {
            this.translateX = node.getTranslateX();
            this.translateY = node.getTranslateY();
            this.scaleX = node.getScaleX();
            this.scaleY = node.getScaleY();
        }
    }
    public final class GlitchFX {

    public GlitchFX() {}

    public static GlitchHandle start(Node node) {
        return start(node, 70, 3.0, 1.5, 2.0);
    }

    /**
     * @param intervalMs how fast the glitch updates (lower = more intense)
     * @param maxX max translateX jitter
     * @param maxY max translateY jitter
     * @param maxRotateDeg max rotation jitter in degrees
     */
    public static GlitchHandle start(Node node, int intervalMs,
                                     double maxX, double maxY, double maxRotateDeg) {

        Random r = new Random();

        // Save original state so we can restore
        final double baseTx = node.getTranslateX();
        final double baseTy = node.getTranslateY();
        final double baseRot = node.getRotate();
        final double baseOpacity = node.getOpacity();
        final double baseScaleX = node.getScaleX();
        final double baseScaleY = node.getScaleY();

        Timeline t = new Timeline(new KeyFrame(Duration.millis(intervalMs), e -> {
            // jitter
            node.setTranslateX(baseTx + rand(r, -maxX, maxX));
            node.setTranslateY(baseTy + rand(r, -maxY, maxY));

            // tiny rotation (optional but looks "digital")
            node.setRotate(baseRot + rand(r, -maxRotateDeg, maxRotateDeg));

            // flicker
            if (r.nextDouble() < 0.25) {
                node.setOpacity(clamp(0.55 + r.nextDouble() * 0.45, 0.0, 1.0));
            } else {
                node.setOpacity(baseOpacity);
            }

            // micro scale pop (optional)
            if (r.nextDouble() < 0.15) {
                node.setScaleX(baseScaleX + rand(r, -0.03, 0.03));
                node.setScaleY(baseScaleY + rand(r, -0.03, 0.03));
            } else {
                node.setScaleX(baseScaleX);
                node.setScaleY(baseScaleY);
            }
        }));

        t.setCycleCount(Animation.INDEFINITE);
        t.play();

        return new GlitchHandle() {
            @Override public void stop() {
                t.stop();
                // Restore original state
                node.setTranslateX(baseTx);
                node.setTranslateY(baseTy);
                node.setRotate(baseRot);
                node.setOpacity(baseOpacity);
                node.setScaleX(baseScaleX);
                node.setScaleY(baseScaleY);
            }

            @Override public Timeline timeline() {
                return t;
            }
        };
    }

    private static double rand(Random r, double min, double max) {
        return min + (max - min) * r.nextDouble();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public interface GlitchHandle {
        void stop();
        Timeline timeline();
    }
}

}

  }
    
