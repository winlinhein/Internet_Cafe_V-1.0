package animation;

import javafx.animation.FadeTransition;
import javafx.animation.FillTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class NovaFX {
    private static final Interpolator POP = Interpolator.SPLINE(0.14, 0.92, 0.18, 1.0);
    private static final Interpolator DRAWER = Interpolator.SPLINE(0.12, 0.94, 0.15, 1.0);

    private NovaFX() {}

    public static void preparePopup(Node modal) {
        if (modal == null) return;
        modal.setOpacity(0.0);
        modal.setTranslateY(28.0);
        modal.setScaleX(0.94);
        modal.setScaleY(0.94);
    }

    public static void prepareDrawer(Node drawer) {
        if (drawer == null) return;
        drawer.setOpacity(0.0);
        drawer.setTranslateX(64.0);
        drawer.setScaleX(0.978);
        drawer.setScaleY(0.978);
    }

    public static void showDrawer(StackPane overlay, Node drawer) {
        if (overlay == null || drawer == null) return;
        overlay.setOpacity(0.0);
        prepareDrawer(drawer);

        FadeTransition bg = new FadeTransition(Duration.millis(180), overlay);
        bg.setToValue(1.0);

        FadeTransition fade = new FadeTransition(Duration.millis(240), drawer);
        fade.setToValue(1.0);

        TranslateTransition entry = new TranslateTransition(Duration.millis(360), drawer);
        entry.setFromX(86.0);
        entry.setToX(-8.0);
        entry.setInterpolator(DRAWER);

        TranslateTransition settle = new TranslateTransition(Duration.millis(130), drawer);
        settle.setFromX(-8.0);
        settle.setToX(0.0);
        settle.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(260), drawer);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition firstPass = new ParallelTransition(bg, fade, entry, scale, createGlitchBurst(drawer, 0.75));
        SequentialTransition open = new SequentialTransition(firstPass, settle);
        open.setOnFinished(e -> pulseGlow(drawer));
        open.play();
    }

    public static void hideDrawer(StackPane overlay, Node drawer, Runnable done) {
        if (overlay == null || drawer == null) {
            if (done != null) done.run();
            return;
        }
        FadeTransition bg = new FadeTransition(Duration.millis(150), overlay);
        bg.setToValue(0.0);

        FadeTransition fade = new FadeTransition(Duration.millis(160), drawer);
        fade.setToValue(0.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(190), drawer);
        slide.setToX(56.0);
        slide.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale = new ScaleTransition(Duration.millis(180), drawer);
        scale.setToX(0.985);
        scale.setToY(0.985);

        ParallelTransition out = new ParallelTransition(bg, fade, slide, scale);
        out.setOnFinished(e -> {
            drawer.setTranslateX(0.0);
            drawer.setOpacity(1.0);
            drawer.setScaleX(1.0);
            drawer.setScaleY(1.0);
            if (done != null) done.run();
        });
        out.play();
    }

    public static void showPopup(StackPane overlay, Node modal) {
        if (overlay == null || modal == null) return;
        overlay.setOpacity(0.0);
        preparePopup(modal);

        FadeTransition bg = new FadeTransition(Duration.millis(170), overlay);
        bg.setToValue(1.0);

        FadeTransition fade = new FadeTransition(Duration.millis(220), modal);
        fade.setToValue(1.0);

        TranslateTransition rise = new TranslateTransition(Duration.millis(290), modal);
        rise.setFromY(34.0);
        rise.setToY(-6.0);
        rise.setInterpolator(POP);

        TranslateTransition settle = new TranslateTransition(Duration.millis(120), modal);
        settle.setFromY(-6.0);
        settle.setToY(0.0);
        settle.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(300), modal);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(POP);

        ParallelTransition entry = new ParallelTransition(bg, fade, rise, scale, createGlitchBurst(modal, 1.0), createScanlineSweep(modal));
        new SequentialTransition(entry, settle).play();
    }

    public static void hidePopup(StackPane overlay, Node modal, Runnable done) {
        if (overlay == null || modal == null) {
            if (done != null) done.run();
            return;
        }
        FadeTransition bg = new FadeTransition(Duration.millis(130), overlay);
        bg.setToValue(0.0);

        FadeTransition fade = new FadeTransition(Duration.millis(130), modal);
        fade.setToValue(0.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(130), modal);
        scale.setToX(0.972);
        scale.setToY(0.972);

        TranslateTransition drop = new TranslateTransition(Duration.millis(140), modal);
        drop.setToY(18.0);

        ParallelTransition out = new ParallelTransition(bg, fade, scale, drop);
        out.setOnFinished(e -> {
            modal.setTranslateY(0.0);
            modal.setOpacity(1.0);
            modal.setScaleX(1.0);
            modal.setScaleY(1.0);
            if (done != null) done.run();
        });
        out.play();
    }

    public static void installCardEnergy(Node node) {
        if (node == null) return;
        node.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(170), node);
            scale.setToX(1.022);
            scale.setToY(1.022);
            TranslateTransition lift = new TranslateTransition(Duration.millis(170), node);
            lift.setToY(-4.0);
            ParallelTransition hover = new ParallelTransition(scale, lift, createGlitchBurst(node, 0.45));
            hover.play();
        });
        node.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(170), node);
            scale.setToX(1.0);
            scale.setToY(1.0);
            TranslateTransition lift = new TranslateTransition(Duration.millis(170), node);
            lift.setToY(0.0);
            new ParallelTransition(scale, lift).play();
        });
    }

    public static void revealCard(Node node, int index) {
        if (node == null) return;
        node.setOpacity(0.0);
        node.setTranslateY(16.0);
        node.setScaleX(0.985);
        node.setScaleY(0.985);

        Duration delay = Duration.millis(index * 22L);
        FadeTransition fade = new FadeTransition(Duration.millis(210), node);
        fade.setToValue(1.0);
        fade.setDelay(delay);

        TranslateTransition slide = new TranslateTransition(Duration.millis(260), node);
        slide.setToY(0.0);
        slide.setDelay(delay);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(240), node);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setDelay(delay);

        ParallelTransition pt = new ParallelTransition(fade, slide, scale);
        pt.setOnFinished(e -> createGlitchBurst(node, 0.35).play());
        pt.play();
    }

    public static void animateWidth(Region region, double width) {
        if (region == null) return;
        new Timeline(new KeyFrame(Duration.millis(160), new KeyValue(region.prefWidthProperty(), width, Interpolator.EASE_BOTH))).play();
    }

    public static void pulseGlow(Node node) {
        if (node == null) return;
        DropShadow ds = new DropShadow();
        ds.setRadius(18.0);
        ds.setSpread(0.06);
        ds.setOffsetY(4.0);
        ds.setColor(Color.rgb(83, 221, 255, 0.18));
        node.setEffect(ds);
        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(ds.radiusProperty(), 18.0),
                new KeyValue(ds.colorProperty(), Color.rgb(83, 221, 255, 0.16))),
            new KeyFrame(Duration.millis(360),
                new KeyValue(ds.radiusProperty(), 24.0),
                new KeyValue(ds.colorProperty(), Color.rgb(255, 84, 194, 0.16))),
            new KeyFrame(Duration.millis(760),
                new KeyValue(ds.radiusProperty(), 18.0),
                new KeyValue(ds.colorProperty(), Color.rgb(83, 221, 255, 0.16)))
        );
        tl.play();
    }

    public static void installButtonPulse(Node... nodes) {
        if (nodes == null) return;
        for (Node node : nodes) {
            if (node == null) continue;
            node.setOnMousePressed(e -> {
                ScaleTransition press = new ScaleTransition(Duration.millis(85), node);
                press.setToX(0.96);
                press.setToY(0.96);
                press.play();
            });
            node.setOnMouseReleased(e -> {
                ScaleTransition release = new ScaleTransition(Duration.millis(110), node);
                release.setToX(1.0);
                release.setToY(1.0);
                release.play();
            });
        }
    }

    public static void installAccentFlicker(Labeled... labels) {
        if (labels == null) return;
        for (Labeled label : labels) {
            if (label == null) continue;
            Paint fill = label.getTextFill();
            if (fill instanceof Color) {
                label.setTextFill((Color) fill);
            }
            // Keep label colors stable. The project still keeps card and modal hover
            // animations, but label-level jitter/flicker is intentionally disabled.
            label.setOnMouseEntered(null);
            label.setOnMouseExited(null);
        }
    }

    public static void installHistoryPulse(Node node) {
        if (node == null) return;
        node.setOnMouseEntered(e -> {
            TranslateTransition shift = new TranslateTransition(Duration.millis(150), node);
            shift.setToX(4.0);
            shift.play();
            createGlitchBurst(node, 0.25).play();
        });
        node.setOnMouseExited(e -> {
            TranslateTransition shift = new TranslateTransition(Duration.millis(150), node);
            shift.setToX(0.0);
            shift.play();
        });
    }

    public static SequentialTransition createGlitchBurst(Node node, double strength) {
        if (node == null) return new SequentialTransition();
        double s = Math.max(0.25, Math.min(1.0, strength));
        TranslateTransition a = new TranslateTransition(Duration.millis(18), node);
        a.setByX(-1.3 * s);
        TranslateTransition b = new TranslateTransition(Duration.millis(18), node);
        b.setByX(2.2 * s);
        TranslateTransition c = new TranslateTransition(Duration.millis(18), node);
        c.setByX(-1.1 * s);
        TranslateTransition d = new TranslateTransition(Duration.millis(16), node);
        d.setToX(0.0);

        FadeTransition f1 = new FadeTransition(Duration.millis(20), node);
        f1.setToValue(Math.max(0.84, 1.0 - (0.12 * s)));
        FadeTransition f2 = new FadeTransition(Duration.millis(20), node);
        f2.setToValue(1.0);

        ParallelTransition first = new ParallelTransition(a, f1);
        ParallelTransition second = new ParallelTransition(b, f2);
        ParallelTransition third = new ParallelTransition(c);
        ParallelTransition fourth = new ParallelTransition(d);
        return new SequentialTransition(first, second, third, fourth);
    }

    private static SequentialTransition createScanlineSweep(Node node) {
        if (!(node instanceof Region)) return new SequentialTransition(new PauseTransition(Duration.ZERO));
        Region region = (Region) node;
        Rectangle scan = new Rectangle();
        scan.widthProperty().bind(region.widthProperty().subtract(10.0));
        scan.setHeight(2.0);
        scan.setManaged(false);
        scan.setMouseTransparent(true);
        scan.setFill(Color.rgb(110, 235, 255, 0.45));

        if (region instanceof StackPane) {
            ((StackPane) region).getChildren().add(scan);
        } else {
            return new SequentialTransition(new PauseTransition(Duration.ZERO));
        }

        scan.setTranslateY(-120.0);
        FadeTransition show = new FadeTransition(Duration.millis(50), scan);
        show.setFromValue(0.0);
        show.setToValue(1.0);

        TranslateTransition sweep = new TranslateTransition(Duration.millis(260), scan);
        sweep.setFromY(-120.0);
        sweep.setToY(120.0);

        FadeTransition hide = new FadeTransition(Duration.millis(80), scan);
        hide.setFromValue(1.0);
        hide.setToValue(0.0);

        SequentialTransition seq = new SequentialTransition(show, sweep, hide);
        seq.setOnFinished(e -> {
            if (region instanceof StackPane) ((StackPane) region).getChildren().remove(scan);
        });
        return seq;
    }
}
