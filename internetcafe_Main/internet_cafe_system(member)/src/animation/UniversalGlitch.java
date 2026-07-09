package animation;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.*;

public final class UniversalGlitch {

    private static final String KEY = "px.glitch.handle";

    private final Random rnd = new Random();
    private final Node target;

    private Pane overlay;             // added on top of target's parent
    private ImageView r, g, b;         // RGB ghosts
    private Rectangle scanlines;       // scanline overlay
    private final List<ImageView> slices = new ArrayList<>();
    private final List<Rectangle> sliceClips = new ArrayList<>();

    private Timeline tick;
    private Timeline burst;

    private boolean running;

    private UniversalGlitch(Node target) {
        this.target = target;
    }

    /** Attach glitch to ANY node. Parent must be a Pane (StackPane/AnchorPane/VBox/HBox/etc). */
    public static UniversalGlitch attach(Node target) {
        if (target == null) throw new IllegalArgumentException("target required");
        Object existing = target.getProperties().get(KEY);
        if (existing instanceof UniversalGlitch) return (UniversalGlitch) existing;

        UniversalGlitch g = new UniversalGlitch(target);
        target.getProperties().put(KEY, g);
        g.build();
        return g;
    }

    /** Apply glitch to all nodes matching selector under root (e.g., ".glitch"). */
    public static void applyTo(Parent root, String selector) {
        if (root == null || selector == null) return;
        Platform.runLater(() -> {
            for (Node n : safeLookupAll(root, selector)) {
                attach(n).start();
            }
        });
    }

    /** Stop and remove overlay layers. */
    public void stop() {
        running = false;
        if (tick != null) tick.stop();
        if (burst != null) burst.stop();
        reset();
        removeOverlay();
    }

    /** Start glitching. */
    public void start() {
        if (running) return;
        running = true;

        // Ensure overlay exists after layout
        Platform.runLater(() -> {
            if (!ensureOverlay()) return;
            tick.playFromStart();
        });
    }

    // ---------------- internals ----------------

    private static Set<Node> safeLookupAll(Parent root, String selector) {
        try { return root.lookupAll(selector); }
        catch (Exception e) { return Collections.emptySet(); }
    }

    private void build() {
        // Timers
        tick = new Timeline(
                new KeyFrame(Duration.ZERO, e -> micro()),
                new KeyFrame(Duration.millis(55))
        );
        tick.setCycleCount(Animation.INDEFINITE);

        burst = new Timeline(
                new KeyFrame(Duration.ZERO, e -> burst()),
                new KeyFrame(Duration.millis(35))
        );
        burst.setCycleCount(10);
    }

    /** Overlay requires target parent to be a Pane so we can add children above. */
    private boolean ensureOverlay() {
        if (!(target.getParent() instanceof Pane)) {
            // If parent isn't a Pane, glitch still does jitter only (no RGB/slices)
            return false;
        }
        Pane parent = (Pane) target.getParent();

        if (overlay == null) {
            overlay = new Pane();
            overlay.setManaged(false);
            overlay.setMouseTransparent(true);

            // Must be inserted ABOVE the target
            int idx = parent.getChildren().indexOf(target);
            if (idx < 0) idx = parent.getChildren().size();
            parent.getChildren().add(idx + 1, overlay);

            // build visuals once
            makeLayers();
        }

        layoutOverlay();
        parent.layoutBoundsProperty().addListener((obs, o, n) -> layoutOverlay());
        target.boundsInParentProperty().addListener((obs, o, n) -> layoutOverlay());

        return true;
    }

    private void makeLayers() {
        // RGB ghosts are snapshots so they work on ANY node type
        r = new ImageView();
        g = new ImageView();
        b = new ImageView();

        setupGhost(r, 0.75);
        setupGhost(g, 0.55);
        setupGhost(b, 0.70);

        // Screen blend looks neon-glitchy
        r.setBlendMode(BlendMode.SCREEN);
        g.setBlendMode(BlendMode.SCREEN);
        b.setBlendMode(BlendMode.SCREEN);

        // Scanlines
        scanlines = new Rectangle(10, 10);
        scanlines.setFill(Color.rgb(255, 255, 255, 0.08));
        scanlines.setBlendMode(BlendMode.OVERLAY);
        scanlines.setOpacity(0);

        // Slices (tears)
        for (int i = 0; i < 7; i++) {
            ImageView s = new ImageView();
            setupGhost(s, 0.0);
            s.setBlendMode(BlendMode.SCREEN);

            Rectangle clip = new Rectangle(10, 10);
            s.setClip(clip);

            slices.add(s);
            sliceClips.add(clip);
        }

        overlay.getChildren().addAll(r, g, b);
        overlay.getChildren().addAll(slices);
        overlay.getChildren().add(scanlines);
    }

    private void setupGhost(ImageView iv, double opacity) {
        iv.setMouseTransparent(true);
        iv.setManaged(false);
        iv.setOpacity(opacity);
        iv.setSmooth(false);
        iv.setCache(true);
    }

    private void layoutOverlay() {
        if (overlay == null) return;
        Bounds bnd = target.getBoundsInParent();

        overlay.relocate(bnd.getMinX(), bnd.getMinY());
        overlay.resize(bnd.getWidth(), bnd.getHeight());

        scanlines.setWidth(Math.max(1, bnd.getWidth()));
        scanlines.setHeight(Math.max(1, bnd.getHeight()));

        for (Rectangle c : sliceClips) {
            c.setWidth(Math.max(1, bnd.getWidth()));
            c.setHeight(10);
            c.setX(0);
            c.setY(0);
        }
    }

    private void removeOverlay() {
        if (overlay == null) return;
        if (overlay.getParent() instanceof Pane) {
            ((Pane) overlay.getParent()).getChildren().remove(overlay);
        }
        overlay = null;
        r = g = b = null;
        scanlines = null;
        slices.clear();
        sliceClips.clear();
    }

    private void reset() {
        target.setTranslateX(0);
        target.setTranslateY(0);
        target.setRotate(0);
        target.setOpacity(1);
    }

    private WritableImage snap() {
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return target.snapshot(sp, null);
    }

    private void micro() {
        if (!running) return;

        // Always some micro jitter
        if (rnd.nextDouble() < 0.35) {
            target.setTranslateX(rnd.nextDouble() * 1.8 - 0.9);
            target.setTranslateY(rnd.nextDouble() * 1.2 - 0.6);
        } else {
            target.setTranslateX(0);
            target.setTranslateY(0);
        }

        // rare burst trigger
        if (rnd.nextDouble() < 0.06 && burst.getStatus() != Animation.Status.RUNNING) {
            burst.playFromStart();
        }

        // Overlay effects only if overlay exists
        if (overlay == null) return;

        WritableImage img = snap();
        r.setImage(img);
        g.setImage(img);
        b.setImage(img);

        // subtle RGB split
        double rgb = (rnd.nextDouble() < 0.65) ? (rnd.nextDouble() * 3.0) : 0;
        r.setTranslateX(+rgb);
        g.setTranslateX(-rgb * 0.7);
        b.setTranslateX(+rgb * 0.35);

        r.setTranslateY(rnd.nextDouble() < 0.25 ? (rnd.nextDouble() * 1.2 - 0.6) : 0);
        g.setTranslateY(rnd.nextDouble() < 0.18 ? (rnd.nextDouble() * 1.2 - 0.6) : 0);
        b.setTranslateY(rnd.nextDouble() < 0.12 ? (rnd.nextDouble() * 1.2 - 0.6) : 0);

        // scanline flicker
        scanlines.setOpacity(rnd.nextDouble() < 0.28 ? (0.08 + rnd.nextDouble() * 0.20) : 0);

        // occasional slice tear (1–2 slices)
        int hits = (rnd.nextDouble() < 0.22) ? (1 + rnd.nextInt(2)) : 0;
        for (int i = 0; i < slices.size(); i++) {
            ImageView s = slices.get(i);
            Rectangle c = sliceClips.get(i);

            if (i < hits) {
                tear(s, c);
            } else {
                s.setOpacity(0);
                s.setTranslateX(0);
                s.setTranslateY(0);
            }
        }
    }

    private void burst() {
        if (!running) return;

        // Burst mode: big RGB split + many tears + flicker
        target.setOpacity(0.85 + rnd.nextDouble() * 0.15);
        target.setTranslateX(rnd.nextDouble() * 6 - 3);
        target.setTranslateY(rnd.nextDouble() * 5 - 2.5);
        target.setRotate(rnd.nextDouble() * 2 - 1);

        if (overlay == null) return;

        WritableImage img = snap();
        r.setImage(img); g.setImage(img); b.setImage(img);

        double rgb = 6 + rnd.nextDouble() * 10;
        r.setTranslateX(+rgb);
        g.setTranslateX(-rgb * 0.85);
        b.setTranslateX(+rgb * 0.45);

        scanlines.setOpacity(0.18 + rnd.nextDouble() * 0.35);

        for (int i = 0; i < slices.size(); i++) {
            ImageView s = slices.get(i);
            Rectangle c = sliceClips.get(i);
            tear(s, c);
            s.setOpacity(0.25 + rnd.nextDouble() * 0.55);
        }
    }

    private void tear(ImageView s, Rectangle clip) {
        double w = Math.max(1, overlay.getWidth());
        double h = Math.max(1, overlay.getHeight());

        double sliceH = 6 + rnd.nextDouble() * 22;
        double y = rnd.nextDouble() * Math.max(1, (h - sliceH));

        clip.setWidth(w);
        clip.setHeight(sliceH);
        clip.setX(0);
        clip.setY(y);

        double dx = (rnd.nextDouble() * 26) - 13;
        s.setTranslateX(dx);
        s.setTranslateY(rnd.nextDouble() * 2 - 1);
        s.setOpacity(0.35 + rnd.nextDouble() * 0.55);

        // each slice uses current snapshot
        if (s.getImage() == null && r != null) s.setImage(r.getImage());
        else if (r != null) s.setImage(r.getImage());
    }
}