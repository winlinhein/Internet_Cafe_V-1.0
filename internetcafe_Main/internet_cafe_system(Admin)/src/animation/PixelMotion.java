package animation;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.stage.Stage;
import animation.GlitchFX;
import javafx.scene.chart.Chart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import admin_controllers.NotificationPopupController;

import java.io.IOException;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import javafx.scene.control.DateCell;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 * Tiny, dependency-free motion layer for JavaFX (JDK 18 SAFE).
 *
 * Usage (basic):
 *   PixelMotion.applyTo(root);
 *
 * Usage (dashboard extras):
 *   PixelMotion.applyDashboard(root, sidebarRegion, toggleNode, contentStackPane);
 *   // then on buttons:
 *   PixelMotion.toggleSidebar(sidebarRegion);
 *   PixelMotion.showPage(contentStackPane, "pageOverview");
 *
 * Toast:
 *   PixelMotion.toastGlitch(anyNode, "Title", "Message", PixelMotion.ToastType.OK);
 */
public final class PixelMotion {
    private static final Random RNG = new Random();
    private PixelMotion() {}

    /** Call once after the FXML is loaded (initialize() is fine). */
    public static void applyTo(Parent root) {
        if (root == null) return;

        // Run after layout so bounds are valid for nicer motion.
        Platform.runLater(() -> {
            installHoverPress(root, ".btn-blue", 1.03);
            installHoverPress(root, ".btn-pink", 1.03);
            installHoverPress(root, ".btn-white", 1.03);
            installHoverPress(root, ".side-btn", 1.04);
            installHoverPress(root, ".icon-btn", 1.06);

            
            installHoverPress(root, ".qty-btn", 1.08);
            installHoverPress(root, ".prod-edit", 1.04);
            installHoverPress(root, ".product-card", 1.02);
installCardFloat(root, ".card");
            installCardFloat(root, ".panel");
            installCardFloat(root, ".quick-box");
            installCardFloat(root, ".nova-modal-card");
            installCardFloat(root, ".cyber-modal-card");
            installCardFloat(root, ".detail-drawer");
            installCardFloat(root, ".pcs-detail-drawer");
            installCardFloat(root, ".customer-modal");

            installCardFloat(root, ".product-card");
            installThumbPulse(root, ".product-thumb-wrap");
            installClickPop(root, ".qty-btn");
installFocusGlow(root);

            playEntrance(root, Arrays.asList(".card", ".panel", ".quick-box", ".table", ".product-card"));
        });
    }

    // -----------------------------
    // Lookups
    // -----------------------------

    private static Set<Node> lookupAll(Parent root, String selector) {
        try {
            return root.lookupAll(selector);
        } catch (Exception ignored) {
            return Collections.emptySet();
        }
    }

    // -----------------------------
    // Hover / press (DOES NOT overwrite your handlers)
    // -----------------------------

    private static void installHoverPress(Parent root, String selector, double hoverScale) {
        for (Node n : lookupAll(root, selector)) {
            // Skip nodes already wired (avoid double install).
            if (Boolean.TRUE.equals(n.getProperties().get("px.motion.hover"))) continue;
            n.getProperties().put("px.motion.hover", true);

            final Duration dIn = Duration.millis(120);
            final Duration dOut = Duration.millis(140);

            final ScaleTransition stIn = new ScaleTransition(dIn, n);
            stIn.setInterpolator(Interpolator.EASE_OUT);

            final ScaleTransition stOut = new ScaleTransition(dOut, n);
            stOut.setInterpolator(Interpolator.EASE_BOTH);

            // IMPORTANT: addEventHandler so we do NOT kill your controller handlers
            n.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                stIn.stop();
                stIn.setToX(hoverScale);
                stIn.setToY(hoverScale);
                stIn.playFromStart();
            });

            n.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                stOut.stop();
                stOut.setToX(1.0);
                stOut.setToY(1.0);
                stOut.playFromStart();
            });

            n.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                n.setScaleX(0.985);
                n.setScaleY(0.985);
            });

            n.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                boolean hover = n.isHover();
                n.setScaleX(hover ? hoverScale : 1.0);
                n.setScaleY(hover ? hoverScale : 1.0);
            });
        }
    }

    // -----------------------------
    // Card float
    // -----------------------------

    private static void installCardFloat(Parent root, String selector) {
        for (Node n : lookupAll(root, selector)) {
            if (Boolean.TRUE.equals(n.getProperties().get("px.motion.float"))) continue;
            n.getProperties().put("px.motion.float", true);

            final TranslateTransition ttIn = new TranslateTransition(Duration.millis(140), n);
            ttIn.setInterpolator(Interpolator.EASE_OUT);
            final TranslateTransition ttOut = new TranslateTransition(Duration.millis(170), n);
            ttOut.setInterpolator(Interpolator.EASE_BOTH);

            // Use addEventHandler (safe)
            n.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                ttIn.stop();
                ttIn.setToY(-2.5);
                ttIn.playFromStart();
                boostShadow(n, 1.15);
            });

            n.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                ttOut.stop();
                ttOut.setToY(0);
                ttOut.playFromStart();
                boostShadow(n, 1.0);
            });
        }
    }

    private static void boostShadow(Node n, double mult) {
        // Shadow boost disabled: dynamically resizing DropShadow effects on every
        // hover event forces a GPU re-render and contributes to animation lag.
        // The hover scale animation in installCardFloat already provides enough
        // depth feedback without shadow manipulation.
    }

    // -----------------------------
    // Focus glow (TextField)
    // -----------------------------

    private static void installFocusGlow(Parent root) {
        for (Node n : lookupAll(root, ".text-field")) {
            if (!(n instanceof TextField)) continue;
            final TextField tf = (TextField) n;

            if (Boolean.TRUE.equals(tf.getProperties().get("px.motion.focus"))) continue;
            tf.getProperties().put("px.motion.focus", true);

            ChangeListener<Boolean> listener = (obs, oldV, focused) -> {
                if (focused) startPulseGlow(tf);
                else stopPulseGlow(tf);
            };
            tf.focusedProperty().addListener(listener);
        }
    }

    private static void startPulseGlow(Node n) {
        Object existing = n.getProperties().get("px.motion.pulse");
        if (existing instanceof Timeline) {
            ((Timeline) existing).play();
            return;
        }

        DropShadow ds = new DropShadow();
        ds.setColor(Color.rgb(120, 220, 255, 0.55));
        ds.setRadius(14);
        ds.setSpread(0.08);
        ds.setOffsetY(0);
        n.setEffect(ds);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(ds.radiusProperty(), 14, Interpolator.EASE_BOTH),
                        new KeyValue(ds.spreadProperty(), 0.08, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(700),
                        new KeyValue(ds.radiusProperty(), 20, Interpolator.EASE_BOTH),
                        new KeyValue(ds.spreadProperty(), 0.13, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(1400),
                        new KeyValue(ds.radiusProperty(), 14, Interpolator.EASE_BOTH),
                        new KeyValue(ds.spreadProperty(), 0.08, Interpolator.EASE_BOTH)
                )
        );
        tl.setCycleCount(Animation.INDEFINITE);

        n.getProperties().put("px.motion.pulse", tl);
        tl.play();
    }

    private static void stopPulseGlow(Node n) {
        Object o = n.getProperties().get("px.motion.pulse");
        if (o instanceof Timeline) ((Timeline) o).stop();
        // Let CSS handle normal state.
    }

    // -----------------------------
    // Entrance (cards/panels)
    // -----------------------------

    private static void playEntrance(Parent root, List<String> selectors) {
        List<Node> nodes = selectors.stream()
                .flatMap(sel -> lookupAll(root, sel).stream())
                // stable order: top-to-bottom based on layout bounds
                .sorted(Comparator.comparingDouble(PixelMotion::topThenLeft))
                .collect(Collectors.toList());

        // Pre-hide all nodes synchronously before any frame is rendered,
        // so users never see the "flash of visible cards then disappear" artifact.
        for (Node n : nodes) {
            if (!Boolean.TRUE.equals(n.getProperties().get("px.motion.entrance"))) {
                n.setOpacity(0);
                n.setTranslateY(10);
            }
        }

        int i = 0;
        for (Node n : nodes) {
            if (Boolean.TRUE.equals(n.getProperties().get("px.motion.entrance"))) continue;
            n.getProperties().put("px.motion.entrance", true);

            // Enable render cache during animation to reduce GPU pressure
            n.setCache(true);
            n.setCacheHint(javafx.scene.CacheHint.SPEED);

            FadeTransition ft = new FadeTransition(Duration.millis(200), n);
            ft.setToValue(1);
            ft.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition tt = new TranslateTransition(Duration.millis(220), n);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition pt = new ParallelTransition(ft, tt);
            // No base delay - start immediately, short stagger so cards don't lag behind
            pt.setDelay(Duration.millis(i * 15L));
            pt.setOnFinished(e -> {
                n.setCache(false);
                n.setCacheHint(javafx.scene.CacheHint.DEFAULT);
            });
            pt.play();

            i++;
            if (i > 18) break; // keep it snappy
        }
    }

    private static double topThenLeft(Node n) {
        Bounds b = n.localToScene(n.getBoundsInLocal());
        return b.getMinY() * 10000 + b.getMinX();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // -----------------------------
    // Dashboard extras (sidebar + page transitions + toasts)
    // -----------------------------

    /** Call this instead of applyTo() if you want sidebar collapse + page transitions + toast support wired. */
    public static void applyDashboard(Parent root,
                                     javafx.scene.layout.Region sidebar,
                                     Node toggleButton,
                                     javafx.scene.layout.StackPane contentStack) {
        applyTo(root);

        Platform.runLater(() -> {
            if (sidebar != null && toggleButton != null) {
                installSidebarCollapse(sidebar, toggleButton);
            }
            if (contentStack != null) {
                normalizeStackPages(contentStack);
            }
        });
    }

    /** Toggle sidebar from your controller button handler. */
    public static void toggleSidebar(javafx.scene.layout.Region sidebar) {
        if (sidebar == null) return;

        // ✅ debounce: ignore rapid double triggers (ActionEvent + MouseClicked, etc.)
        long now = System.currentTimeMillis();
        Object lastObj = sidebar.getProperties().get("px.sidebar.lastToggleMs");
        long last = (lastObj instanceof Long) ? (Long) lastObj : 0L;
        if (now - last < 220) return; // ignore second toggle within 220ms
        sidebar.getProperties().put("px.sidebar.lastToggleMs", now);

        Object v = sidebar.getProperties().get("px.sidebar.collapsed");
        boolean collapsed = (v instanceof Boolean) ? (Boolean) v : sidebar.getStyleClass().contains("collapsed");

        setSidebarCollapsed(sidebar, !collapsed, true);
    }

    /** Switches pages inside a StackPane using a smooth slide+fade transition. */
    /**
     * Clears the entrance-animation guard flag from all card/panel nodes inside
     * a page root so that the next call to playEntrance() (or a spawn method)
     * will re-animate them. Call this before showing a page to enable the
     * pop-in re-trigger on every page switch.
     */
    private static void clearEntranceFlags(Parent root) {
        if (root == null) return;
        List<String> selectors = Arrays.asList(
                ".card", ".panel", ".quick-box", ".table", ".product-card");
        for (String sel : selectors) {
            for (Node n : lookupAll(root, sel)) {
                n.getProperties().remove("px.motion.entrance");
                // Pre-hide immediately so there is no visible-then-vanish flash
                n.setOpacity(0.0);
                n.setTranslateY(10.0);
            }
        }
    }

    public static void showPage(javafx.scene.layout.StackPane stack, String pageFxId) {
        if (stack == null || pageFxId == null || pageFxId.trim().isEmpty()) return;

        Node next = resolveTopLevelPage(stack, pageFxId);
        if (next == null) return;

        Node current = null;
        for (Node n : stack.getChildren()) {
            if (n.isVisible() && n.isManaged()) {
                current = n;
                break;
            }
        }
        if (current == next) return;

        for (Node n : stack.getChildren()) {
            cleanupFloatingUi(n);
            if (n != current && n != next) {
                n.setVisible(false);
                n.setManaged(false);
                n.setOpacity(1);
                n.setTranslateX(0);
            }
        }

        // Clear entrance flags so cards pop-in again on every page switch
        if (next instanceof Parent) {
            clearEntranceFlags((Parent) next);
        }

        next.toFront();
        next.setVisible(true);
        next.setManaged(true);
        next.setOpacity(0);
        next.setTranslateX(14);

        final Duration in = Duration.millis(180);
        final Duration out = Duration.millis(160);

        FadeTransition fi = new FadeTransition(in, next);
        fi.setToValue(1);
        fi.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition ti = new TranslateTransition(in, next);
        ti.setToX(0);
        ti.setInterpolator(Interpolator.EASE_OUT);

        final Parent nextParent = (next instanceof Parent) ? (Parent) next : null;
        ParallelTransition inAnim = new ParallelTransition(fi, ti);
        inAnim.setOnFinished(e -> {
            next.setOpacity(1);
            next.setTranslateX(0);
            // Fire card pop-in animations after the page slide-in completes
            if (nextParent != null) {
                Platform.runLater(() -> applyTo(nextParent));
            }
        });

        if (current != null) {
            FadeTransition fo = new FadeTransition(out, current);
            fo.setToValue(0);
            fo.setInterpolator(Interpolator.EASE_IN);

            TranslateTransition to = new TranslateTransition(out, current);
            to.setToX(-14);
            to.setInterpolator(Interpolator.EASE_IN);

            ParallelTransition outAnim = new ParallelTransition(fo, to);
            Node finalCurrent = current;
            outAnim.setOnFinished(e -> {
                finalCurrent.setVisible(false);
                finalCurrent.setManaged(false);
                finalCurrent.setOpacity(1);
                finalCurrent.setTranslateX(0);
                inAnim.playFromStart();
            });
            outAnim.playFromStart();
        } else {
            inAnim.playFromStart();
        }
    }


    private static void cleanupFloatingUi(Node root) {
        if (root == null) return;
        hideFloatingNode(root);
        if (root instanceof Parent) {
            Parent parent = (Parent) root;
            for (Node child : parent.getChildrenUnmodifiable()) {
                cleanupFloatingUi(child);
            }
        }
    }

    private static void hideFloatingNode(Node node) {
        if (!(node instanceof StackPane)) return;

        String id = node.getId() == null ? "" : node.getId().toLowerCase(Locale.ROOT);
        List<String> styleClasses = node.getStyleClass();

        boolean isFloatingHost = id.endsWith("overlay")
                || id.endsWith("modalhost")
                || id.endsWith("drawerhost")
                || styleClasses.contains("drawer-overlay")
                || styleClasses.contains("drawer-host")
                || styleClasses.contains("edit-overlay")
                || styleClasses.contains("nova-overlay")
                || styleClasses.contains("pcs-modal-overlay")
                || styleClasses.contains("cyber-popup-overlay");

        if (!isFloatingHost) return;

        node.setVisible(false);
        node.setManaged(false);
        node.setMouseTransparent(true);
        node.setOpacity(1.0);
        node.setTranslateX(0.0);
        node.setTranslateY(0.0);
        node.setScaleX(1.0);
        node.setScaleY(1.0);
    }

    private static Node resolveTopLevelPage(javafx.scene.layout.StackPane stack, String pageFxId) {
        for (Node child : stack.getChildren()) {
            if (pageFxId.equals(child.getId())) {
                return child;
            }
        }
        Node lookedUp = stack.lookup("#" + pageFxId);
        if (lookedUp == null) return null;
        Node cursor = lookedUp;
        while (cursor != null && cursor.getParent() != stack) {
            cursor = cursor.getParent();
        }
        return cursor;
    }

    public enum ToastType { OK, WARN, ERROR, INFO }

    /**
     * Shows a sleek toast notification (bottom-right).
     * NOTE: Timer NEVER pauses (no hover pause logic).
     */
    public static void toastGlitch(Node anyNodeInScene, String title, String message, ToastType type) {
        if (anyNodeInScene == null || anyNodeInScene.getScene() == null || anyNodeInScene.getScene().getWindow() == null) return;

        final javafx.stage.Window window = anyNodeInScene.getScene().getWindow();

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(false);
        popup.setHideOnEscape(true);

        // ---- Big toast root container (StackPane so we can overlay scanlines)
        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
        root.getStyleClass().add("toast-glitch-root");

        // ---- Card (VBox)
        javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(10);
        card.getStyleClass().add("toast-glitch");
        if (type != null) {
            switch (type) {
                case OK:   card.getStyleClass().add("ok");   break;
                case WARN: card.getStyleClass().add("warn"); break;
                case ERROR:card.getStyleClass().add("err");  break;
                case INFO: card.getStyleClass().add("info"); break;
            }
        }

        // Header row: badge + title stack
        javafx.scene.layout.HBox header = new javafx.scene.layout.HBox(10);
        header.getStyleClass().add("toast-glitch-header");

        javafx.scene.control.Label badge = new javafx.scene.control.Label(toastBadge(type));
        badge.getStyleClass().add("toast-badge");

        javafx.scene.layout.StackPane titleStack = new javafx.scene.layout.StackPane();
        titleStack.getStyleClass().add("toast-title-stack");

        javafx.scene.control.Label tMain = new javafx.scene.control.Label(title == null ? "" : title);
        tMain.getStyleClass().add("toast-glitch-title");

        javafx.scene.control.Label tGhost1 = new javafx.scene.control.Label(title == null ? "" : title);
        tGhost1.getStyleClass().add("toast-glitch-title-ghost1");
        tGhost1.setMouseTransparent(true);

        javafx.scene.control.Label tGhost2 = new javafx.scene.control.Label(title == null ? "" : title);
        tGhost2.getStyleClass().add("toast-glitch-title-ghost2");
        tGhost2.setMouseTransparent(true);

        titleStack.getChildren().addAll(tGhost1, tGhost2, tMain);

        javafx.scene.control.Label sub = new javafx.scene.control.Label(toastSubtitle(type));
        sub.getStyleClass().add("toast-glitch-subtitle");

        javafx.scene.layout.VBox titles = new javafx.scene.layout.VBox(2, titleStack, sub);
        javafx.scene.layout.HBox.setHgrow(titles, javafx.scene.layout.Priority.ALWAYS);

        header.getChildren().addAll(badge, titles);

        // Message stack (with ghost layers too)
        javafx.scene.layout.StackPane msgStack = new javafx.scene.layout.StackPane();

        javafx.scene.control.Label mMain = new javafx.scene.control.Label(message == null ? "" : message);
        mMain.getStyleClass().add("toast-glitch-msg");
        mMain.setWrapText(true);

        javafx.scene.control.Label mGhost1 = new javafx.scene.control.Label(message == null ? "" : message);
        mGhost1.getStyleClass().add("toast-glitch-msg-ghost1");
        mGhost1.setWrapText(true);
        mGhost1.setMouseTransparent(true);

        javafx.scene.control.Label mGhost2 = new javafx.scene.control.Label(message == null ? "" : message);
        mGhost2.getStyleClass().add("toast-glitch-msg-ghost2");
        mGhost2.setWrapText(true);
        mGhost2.setMouseTransparent(true);

        msgStack.getChildren().addAll(mGhost1, mGhost2, mMain);

        // Progress bar
        javafx.scene.layout.Region bar = new javafx.scene.layout.Region();
        bar.getStyleClass().add("toast-progress");

        javafx.scene.layout.StackPane progressWrap = new javafx.scene.layout.StackPane(bar);
        progressWrap.getStyleClass().add("toast-progress-wrap");

        card.getChildren().addAll(header, msgStack, progressWrap);

        // Scanline overlay
        javafx.scene.layout.Region scan = new javafx.scene.layout.Region();
        scan.getStyleClass().add("toast-scanlines");
        scan.setMouseTransparent(true);
        scan.setManaged(false);
        scan.setOpacity(0);

        // Vignette/shine overlay
        javafx.scene.layout.Region sheen = new javafx.scene.layout.Region();
        sheen.getStyleClass().add("toast-sheen");
        sheen.setMouseTransparent(true);
        sheen.setManaged(false);
        sheen.setOpacity(0.0);

        root.getChildren().addAll(card, scan, sheen);
        applyToastFallbackStyles(root, card, header, titleStack, badge, tMain, tGhost1, tGhost2,
                sub, mMain, mGhost1, mGhost2, progressWrap, bar, scan, sheen, type);

        // Initial state for entrance animation
        root.setOpacity(0);
        root.setTranslateY(18);
        root.setScaleX(0.96);
        root.setScaleY(0.96);

        popup.getContent().add(root);
        popup.show(window);

        // copy stylesheets
        if (popup.getScene() != null) {
            popup.getScene().getStylesheets().setAll(anyNodeInScene.getScene().getStylesheets());
            attachToastStylesheet(popup);
        }

        final Runnable dismiss = () -> toastDismiss(popup, root);

        // Click toast to dismiss (does NOT affect timer)
        root.setOnMouseClicked(e -> dismiss.run());

        Platform.runLater(() -> {
            // position bottom-right
            double pad = 18;

            root.applyCss();
            root.layout();

            double w = Math.max(root.prefWidth(-1), root.getWidth());
            double h = Math.max(root.prefHeight(-1), root.getHeight());

            double x = window.getX() + window.getWidth() - w - pad;
            double y = window.getY() + window.getHeight() - h - pad;

            popup.setX(x);
            popup.setY(y);

            // entrance
            Timeline in = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(root.opacityProperty(), 0, Interpolator.EASE_OUT),
                            new KeyValue(root.translateYProperty(), 18, Interpolator.EASE_OUT),
                            new KeyValue(root.scaleXProperty(), 0.96, Interpolator.EASE_OUT),
                            new KeyValue(root.scaleYProperty(), 0.96, Interpolator.EASE_OUT)
                    ),
                    new KeyFrame(Duration.millis(220),
                            new KeyValue(root.opacityProperty(), 1, Interpolator.EASE_OUT),
                            new KeyValue(root.translateYProperty(), 0, Interpolator.EASE_OUT),
                            new KeyValue(root.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                            new KeyValue(root.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
                    )
            );
            in.playFromStart();

            // glow breathe
            Timeline glow = startToastGlow(card, type);
            root.getProperties().put("px.toast.glow", glow);

            // ✅ TIMER (NEVER PAUSES)
            double seconds = (type == ToastType.ERROR) ? 4.0 : 3.2;

            Timeline timer = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(bar.scaleXProperty(), 1.0, Interpolator.LINEAR)),
                    new KeyFrame(Duration.seconds(seconds), new KeyValue(bar.scaleXProperty(), 0.0, Interpolator.LINEAR))
            );
            timer.setOnFinished(e -> dismiss.run());
            timer.playFromStart();
            root.getProperties().put("px.toast.timer", timer);

            // Glitch show sequence
            playScanlineFlash(scan, sheen);
            playChromaticGlitch(tGhost1, tGhost2, mGhost1, mGhost2, type);
            playGlitchJitter(root, type);

            Timeline micro = startMicroGlitch(root, scan, tGhost1, tGhost2, type);
            root.getProperties().put("px.toast.micro", micro);
        });
    }

    // ---- internal helpers

    private static void normalizeStackPages(javafx.scene.layout.StackPane stack) {
        boolean foundVisible = false;
        for (Node n : stack.getChildren()) {
            if (!foundVisible && n.isVisible()) {
                n.setManaged(true);
                foundVisible = true;
            } else {
                n.setVisible(false);
                n.setManaged(false);
            }
        }
        if (!foundVisible && !stack.getChildren().isEmpty()) {
            Node n = stack.getChildren().get(0);
            n.setVisible(true);
            n.setManaged(true);
        }
    }

    private static void installSidebarCollapse(javafx.scene.layout.Region sidebar, Node toggleButton) {

        // Cache texts (JDK 18 safe)
        if (sidebar instanceof Parent) {
            Parent p = (Parent) sidebar;
            for (Node n : p.lookupAll(".side-btn")) {
                if (n instanceof Button) {
                    Button b = (Button) n;
                    cacheSideButtonState(b);
                }
            }
        }

        // Default width
        if (sidebar.getPrefWidth() <= 0) sidebar.setPrefWidth(260);
        if (sidebar.getMinWidth() <= 0) sidebar.setMinWidth(78);

        // ✅ IMPORTANT: only wire toggle ONCE
        if (toggleButton != null) {
            if (Boolean.TRUE.equals(toggleButton.getProperties().get("px.toggle.wired"))) return;
            toggleButton.getProperties().put("px.toggle.wired", true);

            toggleButton.setPickOnBounds(true);

            // ✅ For Button: use ONLY ActionEvent (NOT mouse click too)
            if (toggleButton instanceof Button) {
                Button btn = (Button) toggleButton;
                btn.addEventHandler(javafx.event.ActionEvent.ACTION, e -> {
                    toggleSidebar(sidebar);
                    e.consume();
                });
            } else {
                // ✅ For non-button nodes: mouse clicked only
                toggleButton.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
                    toggleSidebar(sidebar);
                    e.consume();
                });
            }
        }
    }

    /**
     * ✅ FIXED: NO "..." when collapsed, even if the Button has an ImageView graphic.
     * - Collapsed: GRAPHIC_ONLY + empty text + CLIP overrun + empty ellipsis string
     * - Expanded : TEXT + graphic on left + normal overrun
     */
    private static void setSidebarCollapsed(javafx.scene.layout.Region sidebar, boolean collapsed, boolean animate) {
        final double expanded = 260;
        final double compact = 78;

        sidebar.getProperties().put("px.sidebar.collapsed", collapsed);

        if (collapsed) {
            if (!sidebar.getStyleClass().contains("collapsed")) sidebar.getStyleClass().add("collapsed");
        } else {
            sidebar.getStyleClass().remove("collapsed");
        }

        // Update side button texts (and kill ellipsis on collapse)
        if (sidebar instanceof Parent) {
            Parent p = (Parent) sidebar;
            for (Node n : p.lookupAll(".side-btn")) {
                if (n instanceof Button) {
                    Button b = (Button) n;
                    cacheSideButtonState(b);

                    String full = String.valueOf(b.getProperties().get("px.fullText"));
                    if (full == null) full = "";

                    if (collapsed) {
                        // ✅ IMPORTANT: remove any text so no overrun occurs
                        b.setText("");
                        b.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);

                        // ✅ stop "..." completely
                        b.setTextOverrun(OverrunStyle.CLIP);
                        b.setEllipsisString("");

                        // ✅ center icon nicely
                        b.setAlignment(Pos.CENTER);

                        // ✅ also hide any Labeled text node if skin keeps it (rare)
                        hideInternalLabeledTextNode(b, true);

                    } else {
                        // Expanded: restore text + layout
                        b.setText(full);
                        b.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);

                        // restore overrun behavior (optional)
                        b.setTextOverrun(OverrunStyle.ELLIPSIS);
                        b.setEllipsisString("…");

                        b.setAlignment(Pos.CENTER_LEFT);

                        hideInternalLabeledTextNode(b, false);
                    }
                }
            }
        }

        double target = collapsed ? compact : expanded;

        // If prefWidth is bound, animate min/max instead
        boolean prefBound = sidebar.prefWidthProperty().isBound();

        if (!animate) {
            if (!prefBound) sidebar.setPrefWidth(target);
            sidebar.setMinWidth(target);
            sidebar.setMaxWidth(target);
            sidebar.requestLayout();
            if (sidebar.getParent() != null) sidebar.getParent().requestLayout();
            return;
        }

        KeyValue kvPref = prefBound
                ? new KeyValue(sidebar.minWidthProperty(), target, Interpolator.EASE_BOTH)
                : new KeyValue(sidebar.prefWidthProperty(), target, Interpolator.EASE_BOTH);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(240),
                        kvPref,
                        new KeyValue(sidebar.minWidthProperty(), target, Interpolator.SPLINE(0.22, 0.88, 0.24, 1.0)),
                        new KeyValue(sidebar.maxWidthProperty(), target, Interpolator.SPLINE(0.22, 0.88, 0.24, 1.0))
                )
        );
        tl.setOnFinished(e -> {
            sidebar.requestLayout();
            if (sidebar.getParent() != null) sidebar.getParent().requestLayout();
        });
        tl.playFromStart();
    }

    private static void cacheSideButtonState(Button b) {
        if (b == null) return;

        // Save original full text
        b.getProperties().putIfAbsent("px.fullText", b.getText());

        // Save alignment to restore (optional)
        b.getProperties().putIfAbsent("px.alignExpanded", b.getAlignment());

        // If you were using "iconText" before, keep it cached too (not required now)
        String full = String.valueOf(b.getProperties().get("px.fullText"));
        b.getProperties().putIfAbsent("px.iconText", extractIcon(full));
    }

    /**
     * Rare edge case: some skins keep a Text node with "…" visible even when text is "".
     * This method hides that internal Text node during collapsed state.
     */
    private static void hideInternalLabeledTextNode(Button b, boolean hide) {
        try {
            // button must be in a Scene for lookup(".text") to work reliably
            if (b.getScene() == null) return;
            // JavaFX Labeled uses .text node for label text
            Set<Node> textNodes = b.lookupAll(".text");
            for (Node t : textNodes) {
                if (t instanceof Text) {
                    t.setVisible(!hide);
                    t.setManaged(!hide);
                }
            }
        } catch (Exception ignored) {}
    }

    private static String extractIcon(String s) {
        if (s == null) return "";
        String t = s.trim();
        int sp = t.indexOf(' ');
        if (sp <= 0) return t;
        return t.substring(0, sp).trim();
    }

    private static String toastBadge(ToastType type) {
        if (type == null) return "•";
        switch (type) {
            case OK: return "✓";
            case WARN: return "!";
            case ERROR: return "×";
            case INFO: return "i";
        }
        return "•";
    }

    private static String toastSubtitle(ToastType type) {
        if (type == null) return "Notification";
        switch (type) {
            case OK: return "Success";
            case WARN: return "Warning";
            case ERROR: return "Error";
            case INFO: return "Info";
        }
        return "Notification";
    }

    private static void attachToastStylesheet(javafx.stage.Popup popup) {
        if (popup == null || popup.getScene() == null) {
            return;
        }

        String[] fallbackSheets = {
                "/css/pixel_admin_reactive.css",
                "/css/pixel_admin_reactive_1.css"
        };

        for (String sheet : fallbackSheets) {
            try {
                java.net.URL url = PixelMotion.class.getResource(sheet);
                if (url == null) {
                    continue;
                }
                String external = url.toExternalForm();
                if (!popup.getScene().getStylesheets().contains(external)) {
                    popup.getScene().getStylesheets().add(external);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void applyToastFallbackStyles(
            javafx.scene.layout.StackPane root,
            javafx.scene.layout.VBox card,
            javafx.scene.layout.HBox header,
            javafx.scene.layout.StackPane titleStack,
            javafx.scene.control.Label badge,
            javafx.scene.control.Label tMain,
            javafx.scene.control.Label tGhost1,
            javafx.scene.control.Label tGhost2,
            javafx.scene.control.Label sub,
            javafx.scene.control.Label mMain,
            javafx.scene.control.Label mGhost1,
            javafx.scene.control.Label mGhost2,
            javafx.scene.layout.StackPane progressWrap,
            javafx.scene.layout.Region bar,
            javafx.scene.layout.Region scan,
            javafx.scene.layout.Region sheen,
            ToastType type) {
        String borderColor;
        String cardColor;
        String accentColor;

        ToastType resolvedType = type == null ? ToastType.INFO : type;
        switch (resolvedType) {
            case OK:
                borderColor = rgba(80, 255, 170, 0.85);
                cardColor = rgba(235, 255, 245, 0.97);
                accentColor = rgba(80, 255, 170, 0.90);
                break;
            case WARN:
                borderColor = rgba(255, 110, 220, 0.90);
                cardColor = rgba(255, 235, 250, 0.97);
                accentColor = rgba(255, 110, 220, 0.90);
                break;
            case ERROR:
                borderColor = rgba(255, 80, 110, 0.90);
                cardColor = rgba(255, 235, 240, 0.97);
                accentColor = rgba(255, 80, 110, 0.92);
                break;
            case INFO:
            default:
                borderColor = rgba(80, 190, 255, 0.85);
                cardColor = rgba(235, 246, 255, 0.97);
                accentColor = rgba(80, 190, 255, 0.90);
                break;
        }

        root.setStyle("-fx-min-width: 340; -fx-max-width: 480;");
        header.setStyle("-fx-alignment: center-left;");
        titleStack.setStyle("-fx-alignment: center-left;");

        card.setStyle(
                "-fx-padding: 14 14 12 14;" +
                "-fx-spacing: 10;" +
                "-fx-background-radius: 16;" +
                "-fx-border-radius: 16;" +
                "-fx-border-width: 2;" +
                "-fx-background-insets: 0;" +
                "-fx-border-insets: 0;" +
                "-fx-background-color: " + cardColor + ";" +
                "-fx-border-color: " + borderColor + ";"
        );

        badge.setStyle(
                "-fx-min-width: 30;" +
                "-fx-min-height: 30;" +
                "-fx-alignment: center;" +
                "-fx-background-radius: 10;" +
                "-fx-text-fill: " + rgba(10, 14, 22, 0.95) + ";" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: 800;" +
                "-fx-background-color: " + rgbaFromString(accentColor, 0.25) + ";"
        );

        String titleStyle = "-fx-text-fill: " + rgba(10, 14, 22, 0.95) + ";-fx-font-size: 15px;-fx-font-weight: 900;";
        tMain.setStyle(titleStyle);
        tGhost1.setStyle("-fx-text-fill: " + rgba(80, 190, 255, 0.70) + ";-fx-font-size: 15px;-fx-font-weight: 900;");
        tGhost2.setStyle("-fx-text-fill: " + rgba(255, 90, 210, 0.55) + ";-fx-font-size: 15px;-fx-font-weight: 900;");
        sub.setStyle("-fx-text-fill: " + rgba(10, 14, 22, 0.55) + ";-fx-font-size: 11px;-fx-font-weight: 700;");

        String messageBase = "-fx-font-size: 12px;-fx-line-spacing: 2px;";
        mMain.setStyle(messageBase + "-fx-text-fill: " + rgba(10, 14, 22, 0.78) + ";");
        mGhost1.setStyle(messageBase + "-fx-text-fill: " + rgba(80, 190, 255, 0.25) + ";");
        mGhost2.setStyle(messageBase + "-fx-text-fill: " + rgba(255, 90, 210, 0.22) + ";");

        progressWrap.setStyle(
                "-fx-background-radius: 999;" +
                "-fx-background-color: " + rgba(0, 0, 0, 0.06) + ";" +
                "-fx-min-height: 6;" +
                "-fx-max-height: 6;"
        );
        bar.setStyle(
                "-fx-background-radius: 999;" +
                "-fx-min-height: 6;" +
                "-fx-max-height: 6;" +
                "-fx-background-color: " + accentColor + ";" +
                "-fx-scale-x: 1.0;"
        );

        scan.setStyle(
                "-fx-background-radius: 16;" +
                "-fx-background-color: linear-gradient(from 0% 0% to 0% 100%," +
                " rgba(255,255,255,0.00) 0%," +
                " rgba(255,255,255,0.08) 48%," +
                " rgba(0,0,0,0.03) 52%," +
                " rgba(255,255,255,0.00) 100%);"
        );
        sheen.setStyle(
                "-fx-background-radius: 16;" +
                "-fx-background-color: linear-gradient(from 0% 0% to 100% 0%," +
                " rgba(255,255,255,0.00) 0%," +
                " rgba(255,255,255,0.22) 50%," +
                " rgba(255,255,255,0.00) 100%);"
        );
    }

    private static String rgba(int red, int green, int blue, double alpha) {
        return String.format(java.util.Locale.US, "rgba(%d, %d, %d, %.3f)", red, green, blue, alpha);
    }

    private static String rgbaFromString(String rgbaValue, double alpha) {
        int start = rgbaValue.indexOf('(');
        int end = rgbaValue.indexOf(')');
        if (start < 0 || end <= start) {
            return rgbaValue;
        }
        String[] parts = rgbaValue.substring(start + 1, end).split(",");
        if (parts.length < 3) {
            return rgbaValue;
        }
        try {
            int red = Integer.parseInt(parts[0].trim());
            int green = Integer.parseInt(parts[1].trim());
            int blue = Integer.parseInt(parts[2].trim());
            return rgba(red, green, blue, alpha);
        } catch (NumberFormatException ignored) {
            return rgbaValue;
        }
    }

    private static void toastDismiss(javafx.stage.Popup popup, Node root) {
        if (popup == null || root == null) return;

        stopTimelineProp(root, "px.toast.timer");
        stopTimelineProp(root, "px.toast.glow");
        stopTimelineProp(root, "px.toast.micro");

        Timeline out = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(root.opacityProperty(), root.getOpacity(), Interpolator.EASE_IN),
                        new KeyValue(root.translateYProperty(), root.getTranslateY(), Interpolator.EASE_IN),
                        new KeyValue(root.scaleXProperty(), root.getScaleX(), Interpolator.EASE_IN),
                        new KeyValue(root.scaleYProperty(), root.getScaleY(), Interpolator.EASE_IN)
                ),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(root.opacityProperty(), 0, Interpolator.EASE_IN),
                        new KeyValue(root.translateYProperty(), 12, Interpolator.EASE_IN),
                        new KeyValue(root.scaleXProperty(), 0.98, Interpolator.EASE_IN),
                        new KeyValue(root.scaleYProperty(), 0.98, Interpolator.EASE_IN)
                )
        );
        out.setOnFinished(e -> popup.hide());
        out.playFromStart();
    }

    private static void stopTimelineProp(Node n, String key) {
        Object o = n.getProperties().get(key);
        if (o instanceof Timeline) ((Timeline) o).stop();
    }

    private static void playScanlineFlash(javafx.scene.layout.Region scan, javafx.scene.layout.Region sheen) {
        if (scan == null) return;

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(scan.opacityProperty(), 0.0),
                        new KeyValue(sheen.opacityProperty(), 0.0)
                ),
                new KeyFrame(Duration.millis(60),
                        new KeyValue(scan.opacityProperty(), 0.65, Interpolator.EASE_OUT),
                        new KeyValue(sheen.opacityProperty(), 0.35, Interpolator.EASE_OUT)
                ),
                new KeyFrame(Duration.millis(160),
                        new KeyValue(scan.opacityProperty(), 0.18, Interpolator.EASE_IN),
                        new KeyValue(sheen.opacityProperty(), 0.10, Interpolator.EASE_IN)
                ),
                new KeyFrame(Duration.millis(260),
                        new KeyValue(scan.opacityProperty(), 0.0, Interpolator.EASE_IN),
                        new KeyValue(sheen.opacityProperty(), 0.0, Interpolator.EASE_IN)
                )
        );
        tl.playFromStart();
    }

    private static void playChromaticGlitch(Node tG1, Node tG2, Node mG1, Node mG2, ToastType type) {
        double amp = (type == ToastType.ERROR) ? 6 : 4;

        // start hidden-ish
        tG1.setOpacity(0);
        tG2.setOpacity(0);
        mG1.setOpacity(0);
        mG2.setOpacity(0);

        Timeline tl = new Timeline(
                k(0,   tG1,  -amp, 0, 0.55),
                k(0,   tG2,   amp, 0, 0.45),
                k(0,   mG1,  -amp, 0, 0.28),
                k(0,   mG2,   amp, 0, 0.22),

                k(55,  tG1,  amp * 0.7, 0, 0.35),
                k(55,  tG2, -amp * 0.8, 0, 0.30),
                k(55,  mG1,  amp * 0.6, 0, 0.18),
                k(55,  mG2, -amp * 0.7, 0, 0.16),

                k(110, tG1, -amp * 0.35, 0, 0.22),
                k(110, tG2,  amp * 0.25, 0, 0.20),
                k(110, mG1, -amp * 0.25, 0, 0.12),
                k(110, mG2,  amp * 0.20, 0, 0.10),

                k(170, tG1, 0, 0, 0.0),
                k(170, tG2, 0, 0, 0.0),
                k(170, mG1, 0, 0, 0.0),
                k(170, mG2, 0, 0, 0.0)
        );
        tl.playFromStart();
    }

    private static KeyFrame k(int ms, Node n, double tx, double ty, double op) {
        return new KeyFrame(Duration.millis(ms),
                new KeyValue(n.translateXProperty(), tx, Interpolator.DISCRETE),
                new KeyValue(n.translateYProperty(), ty, Interpolator.DISCRETE),
                new KeyValue(n.opacityProperty(), op, Interpolator.DISCRETE)
        );
    }

    private static void playGlitchJitter(Node root, ToastType type) {
        double amp = (type == ToastType.ERROR) ? 4.8 : 3.0;
        double rot = (type == ToastType.ERROR) ? 1.4 : 0.9;

        Timeline jitter = new Timeline(
                new KeyFrame(Duration.millis(0),
                        new KeyValue(root.translateXProperty(), 0),
                        new KeyValue(root.rotateProperty(), 0)
                ),
                new KeyFrame(Duration.millis(30),
                        new KeyValue(root.translateXProperty(), -amp, Interpolator.DISCRETE),
                        new KeyValue(root.rotateProperty(), -rot, Interpolator.DISCRETE)
                ),
                new KeyFrame(Duration.millis(60),
                        new KeyValue(root.translateXProperty(), amp, Interpolator.DISCRETE),
                        new KeyValue(root.rotateProperty(), rot, Interpolator.DISCRETE)
                ),
                new KeyFrame(Duration.millis(90),
                        new KeyValue(root.translateXProperty(), -amp * 0.6, Interpolator.DISCRETE),
                        new KeyValue(root.rotateProperty(), -rot * 0.6, Interpolator.DISCRETE)
                ),
                new KeyFrame(Duration.millis(120),
                        new KeyValue(root.translateXProperty(), amp * 0.4, Interpolator.DISCRETE),
                        new KeyValue(root.rotateProperty(), rot * 0.4, Interpolator.DISCRETE)
                ),
                new KeyFrame(Duration.millis(150),
                        new KeyValue(root.translateXProperty(), 0, Interpolator.EASE_OUT),
                        new KeyValue(root.rotateProperty(), 0, Interpolator.EASE_OUT)
                )
        );
        jitter.playFromStart();
    }

    private static Timeline startMicroGlitch(Node root,
                                            javafx.scene.layout.Region scan,
                                            Node g1, Node g2,
                                            ToastType type) {
        // Very subtle periodic glitch so it feels "alive"
        double amp = (type == ToastType.ERROR) ? 2.8 : 1.6;
        int periodMs = (type == ToastType.ERROR) ? 1400 : 1900;

        Timeline tl = new Timeline(new KeyFrame(Duration.millis(periodMs), e -> {
            // 35% chance to glitch
            if (RNG.nextDouble() > 0.35) return;

            // tiny scanline pop
            if (scan != null) {
                Timeline s = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(scan.opacityProperty(), 0.0)),
                        new KeyFrame(Duration.millis(45), new KeyValue(scan.opacityProperty(), 0.28, Interpolator.EASE_OUT)),
                        new KeyFrame(Duration.millis(110), new KeyValue(scan.opacityProperty(), 0.0, Interpolator.EASE_IN))
                );
                s.play();
            }

            // micro jitter
            Timeline j = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(root.translateXProperty(), 0),
                            new KeyValue(root.translateYProperty(), 0)
                    ),
                    new KeyFrame(Duration.millis(25),
                            new KeyValue(root.translateXProperty(), (RNG.nextBoolean() ? amp : -amp), Interpolator.DISCRETE),
                            new KeyValue(root.translateYProperty(), (RNG.nextBoolean() ? 1 : -1), Interpolator.DISCRETE)
                    ),
                    new KeyFrame(Duration.millis(55),
                            new KeyValue(root.translateXProperty(), 0, Interpolator.EASE_OUT),
                            new KeyValue(root.translateYProperty(), 0, Interpolator.EASE_OUT)
                    )
            );
            j.play();

            // tiny chromatic tick
            g1.setOpacity(0.22);
            g2.setOpacity(0.18);
            g1.setTranslateX(-amp);
            g2.setTranslateX(amp);

            PauseTransition p = new PauseTransition(Duration.millis(90));
            p.setOnFinished(ev -> {
                g1.setOpacity(0);
                g2.setOpacity(0);
                g1.setTranslateX(0);
                g2.setTranslateX(0);
            });
            p.play();
        }));
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
        return tl;
    }

    private static Timeline startToastGlow(Node card, ToastType type) {
        DropShadow ds = new DropShadow();
        ds.setRadius(18);
        ds.setSpread(0.08);
        ds.setOffsetY(8);

        ToastType t = (type == null) ? ToastType.INFO : type;
        Color c;
        switch (t) {
            case OK:    c = Color.rgb(120, 255, 190, 0.28); break;
            case WARN:  c = Color.rgb(255, 220, 120, 0.28); break;
            case ERROR: c = Color.rgb(255, 120, 150, 0.30); break;
            case INFO:
            default:    c = Color.rgb(120, 220, 255, 0.26); break;
        }
        ds.setColor(c);

        card.setEffect(ds);

        Timeline glow = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(ds.radiusProperty(), 18, Interpolator.EASE_BOTH),
                        new KeyValue(ds.spreadProperty(), 0.08, Interpolator.EASE_BOTH),
                        new KeyValue(ds.offsetYProperty(), 8, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(900),
                        new KeyValue(ds.radiusProperty(), 24, Interpolator.EASE_BOTH),
                        new KeyValue(ds.spreadProperty(), 0.12, Interpolator.EASE_BOTH),
                        new KeyValue(ds.offsetYProperty(), 10, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(1800),
                        new KeyValue(ds.radiusProperty(), 18, Interpolator.EASE_BOTH),
                        new KeyValue(ds.spreadProperty(), 0.08, Interpolator.EASE_BOTH),
                        new KeyValue(ds.offsetYProperty(), 8, Interpolator.EASE_BOTH)
                )
        );
        glow.setCycleCount(Animation.INDEFINITE);
        glow.play();
        return glow;
    }


public static void installNotificationBellMotion(Node trigger, Node visualRoot) {
        final Node target = (visualRoot != null) ? visualRoot : trigger;
        if (target == null) return;
        if (Boolean.TRUE.equals(target.getProperties().get("px.notification.bell"))) return;
        target.getProperties().put("px.notification.bell", true);

        Timeline idle = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(target.rotateProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(target.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(target.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(target.rotateProperty(), -8, Interpolator.EASE_BOTH),
                        new KeyValue(target.scaleXProperty(), 1.07, Interpolator.EASE_BOTH),
                        new KeyValue(target.scaleYProperty(), 1.07, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(470),
                        new KeyValue(target.rotateProperty(), 7, Interpolator.EASE_BOTH),
                        new KeyValue(target.scaleXProperty(), 1.03, Interpolator.EASE_BOTH),
                        new KeyValue(target.scaleYProperty(), 1.03, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(760),
                        new KeyValue(target.rotateProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(target.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(target.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)
                )
        );
        idle.setDelay(Duration.millis(950));
        idle.setCycleCount(Animation.INDEFINITE);
        idle.play();
        target.getProperties().put("px.notification.bell.idle", idle);

        Node handlerTarget = (trigger != null) ? trigger : target;
        handlerTarget.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            Timeline ping = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(target.scaleXProperty(), target.getScaleX(), Interpolator.EASE_OUT),
                            new KeyValue(target.scaleYProperty(), target.getScaleY(), Interpolator.EASE_OUT)
                    ),
                    new KeyFrame(Duration.millis(130),
                            new KeyValue(target.scaleXProperty(), 1.10, Interpolator.EASE_OUT),
                            new KeyValue(target.scaleYProperty(), 1.10, Interpolator.EASE_OUT)
                    ),
                    new KeyFrame(Duration.millis(240),
                            new KeyValue(target.scaleXProperty(), 1.03, Interpolator.EASE_BOTH),
                            new KeyValue(target.scaleYProperty(), 1.03, Interpolator.EASE_BOTH)
                    )
            );
            ping.play();
        });
    }


    public static void toggleNotificationPopup(Node ownerNode,
                                               Node anchor,
                                               int notificationCount,
                                               List<String> notifications,
                                               Runnable onClearAll,
                                               IntConsumer onCountChanged) {
        if (ownerNode == null || anchor == null || ownerNode.getScene() == null || ownerNode.getScene().getWindow() == null) return;

        Object existing = anchor.getProperties().get("px.notification.popup");
        if (existing instanceof javafx.stage.Popup) {
            javafx.stage.Popup current = (javafx.stage.Popup) existing;
            if (current.isShowing()) {
                animateNotificationPopupOut(anchor, current, null);
                return;
            }
        }

        final javafx.stage.Window window = ownerNode.getScene().getWindow();
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        FXMLLoader loader = new FXMLLoader(PixelMotion.class.getResource("/view/notification_popup.fxml"));
        StackPane root;
        NotificationPopupController controller;
        try {
            root = loader.load();
            controller = loader.getController();
        } catch (IOException ex) {
            return;
        }

        controller.setData(notificationCount, notifications);
        controller.setCountChangedHandler(onCountChanged);
        popup.getContent().add(root);
        popup.show(window);

        if (popup.getScene() != null) {
            popup.getScene().getStylesheets().setAll(ownerNode.getScene().getStylesheets());
        }

        controller.setActions(
                () -> animateNotificationPopupOut(anchor, popup, null),
                () -> animateNotificationPopupOut(anchor, popup, onClearAll)
        );

        popup.setOnHidden(e -> anchor.getProperties().remove("px.notification.popup"));
        anchor.getProperties().put("px.notification.popup", popup);

        Platform.runLater(() -> {
            root.applyCss();
            root.layout();

            javafx.geometry.Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
            double w = Math.max(root.prefWidth(-1), root.getWidth());
            double x = b.getMaxX() - w + 10;
            double y = b.getMaxY() + 12;
            popup.setX(x);
            popup.setY(y);

            animateNotificationPopupIn(root,
                    controller.getCard(),
                    controller.getEdgeGlow(),
                    controller.getScanline(),
                    controller.getBadgeLabel());
            animateNotificationRows(controller.getListBox());
        });
    }

    public static void animatePopupEntrance(Node overlay, Node popup) {
        if (overlay == null || popup == null) return;

        overlay.setOpacity(0.0);
        popup.setOpacity(0.0);
        popup.setTranslateY(20.0);
        popup.setScaleX(0.94);
        popup.setScaleY(0.94);

        FadeTransition overlayFade = new FadeTransition(Duration.millis(170), overlay);
        overlayFade.setFromValue(0.0);
        overlayFade.setToValue(1.0);

        FadeTransition popupFade = new FadeTransition(Duration.millis(180), popup);
        popupFade.setFromValue(0.0);
        popupFade.setToValue(1.0);

        TranslateTransition popupSlide = new TranslateTransition(Duration.millis(220), popup);
        popupSlide.setFromY(20.0);
        popupSlide.setToY(0.0);
        popupSlide.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition popupScale = new ScaleTransition(Duration.millis(220), popup);
        popupScale.setFromX(0.94);
        popupScale.setFromY(0.94);
        popupScale.setToX(1.0);
        popupScale.setToY(1.0);
        popupScale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(overlayFade, popupFade, popupSlide, popupScale).playFromStart();
    }

    private static void animateNotificationPopupIn(Node root,
                                                   Node card,
                                                   Node edgeGlow,
                                                   Node scanline,
                                                   Node badge) {
        root.setOpacity(0);
        root.setTranslateY(-34);
        root.setScaleX(0.94);
        root.setScaleY(0.94);
        card.setOpacity(0.9);
        card.setTranslateY(-24);

        Timeline intro = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(root.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                        new KeyValue(root.translateYProperty(), -34, Interpolator.EASE_OUT),
                        new KeyValue(root.scaleXProperty(), 0.94, Interpolator.EASE_OUT),
                        new KeyValue(root.scaleYProperty(), 0.94, Interpolator.EASE_OUT),
                        new KeyValue(card.opacityProperty(), 0.9, Interpolator.EASE_OUT),
                        new KeyValue(card.translateYProperty(), -24, Interpolator.EASE_OUT)
                ),
                new KeyFrame(Duration.millis(320),
                        new KeyValue(root.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(root.translateYProperty(), 0.0, Interpolator.EASE_OUT),
                        new KeyValue(root.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(root.scaleYProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(card.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(card.translateYProperty(), 0.0, Interpolator.EASE_OUT)
                )
        );
        intro.playFromStart();

        if (edgeGlow != null) {
            Timeline edgePulse = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(edgeGlow.opacityProperty(), 0.30, Interpolator.EASE_BOTH)
                    ),
                    new KeyFrame(Duration.millis(900),
                            new KeyValue(edgeGlow.opacityProperty(), 0.95, Interpolator.EASE_BOTH)
                    ),
                    new KeyFrame(Duration.millis(1800),
                            new KeyValue(edgeGlow.opacityProperty(), 0.34, Interpolator.EASE_BOTH)
                    )
            );
            edgePulse.setCycleCount(Animation.INDEFINITE);
            edgePulse.play();
            root.getProperties().put("px.notification.edgePulse", edgePulse);
        }

        if (scanline != null) {
            Timeline sweep = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(scanline.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                            new KeyValue(scanline.translateYProperty(), -32, Interpolator.EASE_OUT)
                    ),
                    new KeyFrame(Duration.millis(260),
                            new KeyValue(scanline.opacityProperty(), 0.95, Interpolator.EASE_OUT),
                            new KeyValue(scanline.translateYProperty(), 0, Interpolator.EASE_OUT)
                    ),
                    new KeyFrame(Duration.millis(1150),
                            new KeyValue(scanline.opacityProperty(), 0.0, Interpolator.EASE_IN),
                            new KeyValue(scanline.translateYProperty(), 68, Interpolator.EASE_IN)
                    )
            );
            sweep.playFromStart();
        }

        if (badge != null) {
            ScaleTransition badgePop = new ScaleTransition(Duration.millis(240), badge);
            badgePop.setFromX(0.75);
            badgePop.setFromY(0.75);
            badgePop.setToX(1.0);
            badgePop.setToY(1.0);
            badgePop.setInterpolator(Interpolator.EASE_OUT);
            badgePop.playFromStart();
        }
    }

    private static void animateNotificationRows(javafx.scene.layout.VBox listBox) {
        int idx = 0;
        for (Node row : listBox.getChildren()) {
            row.setOpacity(0);
            row.setTranslateY(-14);
            row.setScaleX(0.985);
            row.setScaleY(0.985);

            Timeline rowIn = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(row.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                            new KeyValue(row.translateYProperty(), -14, Interpolator.EASE_OUT),
                            new KeyValue(row.scaleXProperty(), 0.985, Interpolator.EASE_OUT),
                            new KeyValue(row.scaleYProperty(), 0.985, Interpolator.EASE_OUT)
                    ),
                    new KeyFrame(Duration.millis(210 + idx * 52L),
                            new KeyValue(row.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                            new KeyValue(row.translateYProperty(), 0.0, Interpolator.EASE_OUT),
                            new KeyValue(row.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                            new KeyValue(row.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
                    )
            );
            rowIn.playFromStart();
            idx++;
        }
    }

    private static void animateNotificationPopupOut(Node anchor,
                                                    javafx.stage.Popup popup,
                                                    Runnable onHiddenAction) {
        if (popup == null) return;
        Node root = popup.getContent().isEmpty() ? null : (popup.getContent().get(0) instanceof Node ? (Node) popup.getContent().get(0) : null);

        if (root == null) {
            popup.hide();
            if (anchor != null) anchor.getProperties().remove("px.notification.popup");
            if (onHiddenAction != null) onHiddenAction.run();
            return;
        }

        Object edgePulse = root.getProperties().get("px.notification.edgePulse");
        if (edgePulse instanceof Animation) {
            ((Animation) edgePulse).stop();
        }

        Timeline outro = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(root.opacityProperty(), root.getOpacity(), Interpolator.EASE_BOTH),
                        new KeyValue(root.translateYProperty(), root.getTranslateY(), Interpolator.EASE_BOTH),
                        new KeyValue(root.scaleXProperty(), root.getScaleX(), Interpolator.EASE_BOTH),
                        new KeyValue(root.scaleYProperty(), root.getScaleY(), Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(root.opacityProperty(), 0.0, Interpolator.EASE_BOTH),
                        new KeyValue(root.translateYProperty(), -18, Interpolator.EASE_BOTH),
                        new KeyValue(root.scaleXProperty(), 0.96, Interpolator.EASE_BOTH),
                        new KeyValue(root.scaleYProperty(), 0.96, Interpolator.EASE_BOTH)
                )
        );
        outro.setOnFinished(e -> {
            popup.hide();
            if (anchor != null) anchor.getProperties().remove("px.notification.popup");
            if (onHiddenAction != null) onHiddenAction.run();
        });
        outro.playFromStart();
    }

    private static void installThumbPulse(Parent root, String selector) {
    for (Node n : lookupAll(root, selector)) {
        if (Boolean.TRUE.equals(n.getProperties().get("px.motion.thumb"))) continue;
        n.getProperties().put("px.motion.thumb", true);

        n.setPickOnBounds(false);

        final ScaleTransition in = new ScaleTransition(Duration.millis(160), n);
        in.setInterpolator(Interpolator.EASE_OUT);

        final Timeline idle = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(n.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(n.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(650),
                        new KeyValue(n.scaleXProperty(), 1.045, Interpolator.EASE_BOTH),
                        new KeyValue(n.scaleYProperty(), 1.045, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1300),
                        new KeyValue(n.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(n.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        idle.setCycleCount(Animation.INDEFINITE);

        n.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            in.stop();
            in.setToX(1.03);
            in.setToY(1.03);
            in.playFromStart();
            idle.playFromStart();
        });

        n.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            idle.stop();
            n.setScaleX(1.0);
            n.setScaleY(1.0);
        });
    }
    }
    private static void installClickPop(Parent root, String selector) {
    for (Node n : lookupAll(root, selector)) {
        if (Boolean.TRUE.equals(n.getProperties().get("px.motion.clickpop"))) continue;
        n.getProperties().put("px.motion.clickpop", true);

        n.setPickOnBounds(false);

        n.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
            ScaleTransition shrink = new ScaleTransition(Duration.millis(55), n);
            shrink.setToX(0.90);
            shrink.setToY(0.90);

            ScaleTransition pop = new ScaleTransition(Duration.millis(110), n);
            pop.setToX(1.10);
            pop.setToY(1.10);
            pop.setInterpolator(Interpolator.EASE_OUT);

            ScaleTransition settle = new ScaleTransition(Duration.millis(110), n);
            settle.setToX(1.0);
            settle.setToY(1.0);
            settle.setInterpolator(Interpolator.EASE_BOTH);

            new SequentialTransition(shrink, pop, settle).playFromStart();
        });
    }
}

    // -----------------------------
    // Overview page animations
    // -----------------------------

    public static void applyOverviewAnimations(
            Parent root,
            Label lblOverviewTitle,
            Label lblTopSellTitle,
            Label lblPcStatusTitle,
            Label lblIncomeTitle,
            Label lblPeakTitle,
            Node cardActive,
            Node cardIncomeDay,
            Node cardIncomeMonth,
            Node panelTopSell,
            Node panelPcStatus,
            Node panelIncome,
            Node panelPeakHours,
            StackPane topSellChartHolder,
            StackPane pcActivityChartHolder,
            StackPane incomeChartHolder,
            StackPane peakHoursChartHolder
    ) {
        if (root != null) {
            applyTo(root);
        }

        Platform.runLater(() -> {
            installOverviewTitleEffects(
                    lblOverviewTitle,
                    lblTopSellTitle,
                    lblPcStatusTitle,
                    lblIncomeTitle,
                    lblPeakTitle
            );

            installOverviewCardEntryAnimations(
                    cardActive,
                    cardIncomeDay,
                    cardIncomeMonth,
                    panelTopSell,
                    panelPcStatus,
                    panelIncome,
                    panelPeakHours
            );

            installOverviewHoverPulse(cardActive, false);
            installOverviewHoverPulse(cardIncomeDay, false);
            installOverviewHoverPulse(cardIncomeMonth, false);
            installOverviewHoverPulse(panelTopSell, true);
            installOverviewHoverPulse(panelPcStatus, true);
            installOverviewHoverPulse(panelIncome, true);
            installOverviewHoverPulse(panelPeakHours, true);

            installOverviewSparkleLayer(topSellChartHolder, 8);
            installOverviewSparkleLayer(pcActivityChartHolder, 8);
            installOverviewSparkleLayer(incomeChartHolder, 7);
            installOverviewSparkleLayer(peakHoursChartHolder, 7);
        });
    }

    // ==================== SUPERCOOL CHART ANIMATIONS ====================

    /**
     * Main entry: plays supercool entrance + per-node animations on any chart.
     * Call this every time data is updated or the overview page is switched to.
     */
    public static void animateOverviewChart(Node chart, boolean pie) {
        if (chart == null) return;

        // Stop any running animation tags from a prior call
        Object prior = chart.getProperties().get("px.chart.intro");
        if (prior instanceof Animation a) a.stop();

        if (pie) {
            // Pie: dramatic spin-in from zero, with a spring overshoot
            chart.setOpacity(0);
            chart.setScaleX(0.0);
            chart.setScaleY(0.0);
            chart.setRotate(-340.0);

            Timeline intro = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(chart.opacityProperty(),    0.0),
                    new KeyValue(chart.scaleXProperty(),     0.0),
                    new KeyValue(chart.scaleYProperty(),     0.0),
                    new KeyValue(chart.rotateProperty(),     -340.0)
                ),
                new KeyFrame(Duration.millis(680),
                    new KeyValue(chart.opacityProperty(),    1.0,  Interpolator.EASE_OUT),
                    new KeyValue(chart.scaleXProperty(),     1.08, Interpolator.SPLINE(0.12, 0.95, 0.22, 1.0)),
                    new KeyValue(chart.scaleYProperty(),     1.08, Interpolator.SPLINE(0.12, 0.95, 0.22, 1.0)),
                    new KeyValue(chart.rotateProperty(),     12.0, Interpolator.SPLINE(0.12, 0.95, 0.22, 1.0))
                ),
                new KeyFrame(Duration.millis(880),
                    new KeyValue(chart.scaleXProperty(),     0.96, Interpolator.EASE_BOTH),
                    new KeyValue(chart.scaleYProperty(),     0.96, Interpolator.EASE_BOTH),
                    new KeyValue(chart.rotateProperty(),     -4.0, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(1050),
                    new KeyValue(chart.scaleXProperty(),     1.0,  Interpolator.EASE_BOTH),
                    new KeyValue(chart.scaleYProperty(),     1.0,  Interpolator.EASE_BOTH),
                    new KeyValue(chart.rotateProperty(),     0.0,  Interpolator.EASE_BOTH)
                )
            );
            chart.getProperties().put("px.chart.intro", intro);
            intro.play();
        } else {
            // Line/Bar: slide up + reveal with elastic bounce
            chart.setOpacity(0);
            chart.setTranslateY(40);
            chart.setScaleX(0.88);
            chart.setScaleY(0.88);

            Timeline intro = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(chart.opacityProperty(),    0.0),
                    new KeyValue(chart.translateYProperty(), 40.0),
                    new KeyValue(chart.scaleXProperty(),     0.88),
                    new KeyValue(chart.scaleYProperty(),     0.88)
                ),
                new KeyFrame(Duration.millis(480),
                    new KeyValue(chart.opacityProperty(),    1.0,  Interpolator.EASE_OUT),
                    new KeyValue(chart.translateYProperty(), -6.0, Interpolator.SPLINE(0.12, 0.95, 0.22, 1.0)),
                    new KeyValue(chart.scaleXProperty(),     1.02, Interpolator.SPLINE(0.12, 0.95, 0.22, 1.0)),
                    new KeyValue(chart.scaleYProperty(),     1.02, Interpolator.SPLINE(0.12, 0.95, 0.22, 1.0))
                ),
                new KeyFrame(Duration.millis(640),
                    new KeyValue(chart.translateYProperty(), 3.0,  Interpolator.EASE_BOTH),
                    new KeyValue(chart.scaleXProperty(),     0.99, Interpolator.EASE_BOTH),
                    new KeyValue(chart.scaleYProperty(),     0.99, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(780),
                    new KeyValue(chart.translateYProperty(), 0.0,  Interpolator.EASE_BOTH),
                    new KeyValue(chart.scaleXProperty(),     1.0,  Interpolator.EASE_BOTH),
                    new KeyValue(chart.scaleYProperty(),     1.0,  Interpolator.EASE_BOTH)
                )
            );
            chart.getProperties().put("px.chart.intro", intro);
            intro.play();
        }

        // After the scene graph lays out nodes, animate each data node
        if (chart instanceof Chart) {
            Timeline nodeDelay = new Timeline(new KeyFrame(Duration.millis(pie ? 200 : 120), e -> {
                styleOverviewChartNodes((Chart) chart);
                animateOverviewChartNodes((Chart) chart);
            }));
            nodeDelay.play();
        }
    }

    /**
     * Applies persistent glow + CSS classes on chart data nodes.
     */
    private static void styleOverviewChartNodes(Chart chart) {
        if (chart == null) return;

        if (chart instanceof PieChart pieChart) {
            final String[] NEON_PALETTE = {
                "#ff6b9f", "#5ad8fa", "#7cff7a", "#ffd166",
                "#c084fc", "#ff8a5b", "#00e5a8", "#9ad0f5"
            };
            int index = 0;
            for (PieChart.Data data : pieChart.getData()) {
                Node node = data.getNode();
                if (node != null) {
                    node.getStyleClass().removeIf(s -> s.startsWith("pie-slice-"));
                    node.getStyleClass().add("pie-slice-" + index);
                    String color = NEON_PALETTE[index % NEON_PALETTE.length];
                    node.setStyle("-fx-pie-color: " + color + ";");
                    installOverviewSlicePulse(node, index * 90.0);
                    // Add neon glow on hover
                    installChartNodeHoverGlow(node, color);
                }
                index++;
            }
        } else if (chart instanceof XYChart) {
            for (Node n : chart.lookupAll(".chart-bar")) {
                installOverviewSlicePulse(n, RNG.nextInt(220));
                installChartNodeHoverGlow(n, "#5ad8fa");
            }
            for (Node n : chart.lookupAll(".chart-line-symbol, .chart-area-symbol")) {
                installOverviewSlicePulse(n, RNG.nextInt(200));
                installChartNodeHoverGlow(n, "#7cff7a");
            }
        }
    }

    /**
     * Adds a neon hover glow effect to a chart data node.
     */
    private static void installChartNodeHoverGlow(Node node, String hexColor) {
        if (node == null || Boolean.TRUE.equals(node.getProperties().get("px.chart.hover"))) return;
        node.getProperties().put("px.chart.hover", true);
        try {
            javafx.scene.paint.Color parsed = javafx.scene.paint.Color.web(hexColor);
            DropShadow glow = new DropShadow(22, parsed);
            glow.setSpread(0.3);
            node.setOnMouseEntered(e -> node.setEffect(glow));
            node.setOnMouseExited(e -> node.setEffect(null));
        } catch (Exception ignored) {}
    }

    /**
     * Animates each data node of a chart with per-element staggered entrance.
     * Pie slices fan in with spin, XY bars rocket up from zero height.
     */
    private static void animateOverviewChartNodes(Chart chart) {
        if (chart == null) return;

        if (chart instanceof PieChart pieChart) {
            int index = 0;
            for (PieChart.Data data : pieChart.getData()) {
                Node node = data.getNode();
                if (node == null) continue;

                // Stop existing animations
                Object prev = node.getProperties().get("px.slice.intro");
                if (prev instanceof Animation a) a.stop();

                node.setOpacity(0.0);
                node.setScaleX(0.0);
                node.setScaleY(0.0);
                node.setRotate(-180.0 - (index * 30.0));

                double delay = index * 70.0;
                Timeline sliceIntro = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(node.opacityProperty(),  0.0),
                        new KeyValue(node.scaleXProperty(),   0.0),
                        new KeyValue(node.scaleYProperty(),   0.0),
                        new KeyValue(node.rotateProperty(),   -180.0 - (index * 30.0))
                    ),
                    new KeyFrame(Duration.millis(420 + delay),
                        new KeyValue(node.opacityProperty(),  1.0,  Interpolator.EASE_OUT),
                        new KeyValue(node.scaleXProperty(),   1.1,  Interpolator.SPLINE(0.15, 0.95, 0.2, 1.0)),
                        new KeyValue(node.scaleYProperty(),   1.1,  Interpolator.SPLINE(0.15, 0.95, 0.2, 1.0)),
                        new KeyValue(node.rotateProperty(),   8.0,  Interpolator.SPLINE(0.15, 0.95, 0.2, 1.0))
                    ),
                    new KeyFrame(Duration.millis(580 + delay),
                        new KeyValue(node.scaleXProperty(),   0.97, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(),   0.97, Interpolator.EASE_BOTH),
                        new KeyValue(node.rotateProperty(),  -3.0,  Interpolator.EASE_BOTH)
                    ),
                    new KeyFrame(Duration.millis(700 + delay),
                        new KeyValue(node.scaleXProperty(),   1.0,  Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(),   1.0,  Interpolator.EASE_BOTH),
                        new KeyValue(node.rotateProperty(),   0.0,  Interpolator.EASE_BOTH)
                    )
                );
                final int sliceIndex = index; // capture before increment — lambda requires effectively final
                sliceIntro.setOnFinished(e -> installOverviewSlicePulse(node, sliceIndex * 90.0));
                node.getProperties().put("px.slice.intro", sliceIntro);
                sliceIntro.playFromStart();
                index++;
            }
            return;
        }

        if (chart instanceof XYChart<?, ?> xyChart) {
            // Animate line/bar series nodes — bars rocket up, line draws in
            int seriesIndex = 0;
            for (Object seriesObj : xyChart.getData()) {
                if (!(seriesObj instanceof XYChart.Series<?, ?> series)) continue;

                Node seriesNode = series.getNode();
                if (seriesNode != null) {
                    Object prev = seriesNode.getProperties().get("px.series.intro");
                    if (prev instanceof Animation a) a.stop();

                    seriesNode.setOpacity(0.0);
                    seriesNode.setScaleX(0.0);

                    double seriesDelay = seriesIndex * 80.0;
                    Timeline seriesIntro = new Timeline(
                        new KeyFrame(Duration.ZERO,
                            new KeyValue(seriesNode.opacityProperty(),  0.0),
                            new KeyValue(seriesNode.scaleXProperty(),   0.0)
                        ),
                        new KeyFrame(Duration.millis(600 + seriesDelay),
                            new KeyValue(seriesNode.opacityProperty(),  1.0, Interpolator.EASE_OUT),
                            new KeyValue(seriesNode.scaleXProperty(),   1.0, Interpolator.SPLINE(0.1, 0.9, 0.2, 1.0))
                        )
                    );
                    seriesNode.getProperties().put("px.series.intro", seriesIntro);
                    seriesIntro.playFromStart();
                }

                // Animate each data point / bar
                int pointIndex = 0;
                for (Object dataObj : series.getData()) {
                    if (!(dataObj instanceof XYChart.Data<?, ?> data)) continue;
                    Node pointNode = data.getNode();
                    if (pointNode == null) continue;

                    Object prev = pointNode.getProperties().get("px.point.intro");
                    if (prev instanceof Animation a) a.stop();

                    // Bar: shoot from bottom; symbol: pop in
                    boolean isBar = pointNode.getStyleClass().contains("chart-bar");
                    double delay = pointIndex * 38.0 + seriesIndex * 50.0;

                    if (isBar) {
                        // Scale Y from 0 (bottom anchor via pivot)
                        pointNode.setOpacity(0.0);
                        pointNode.setScaleY(0.0);
                        pointNode.setScaleX(0.6);
                        pointNode.getProperties().put("px.bar.pivot", true);

                        Timeline barIntro = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                new KeyValue(pointNode.opacityProperty(),  0.0),
                                new KeyValue(pointNode.scaleYProperty(),   0.0),
                                new KeyValue(pointNode.scaleXProperty(),   0.6)
                            ),
                            new KeyFrame(Duration.millis(340 + delay),
                                new KeyValue(pointNode.opacityProperty(),  1.0,  Interpolator.EASE_OUT),
                                new KeyValue(pointNode.scaleYProperty(),   1.12, Interpolator.SPLINE(0.1, 0.9, 0.2, 1.0)),
                                new KeyValue(pointNode.scaleXProperty(),   1.06, Interpolator.SPLINE(0.1, 0.9, 0.2, 1.0))
                            ),
                            new KeyFrame(Duration.millis(460 + delay),
                                new KeyValue(pointNode.scaleYProperty(),   0.95, Interpolator.EASE_BOTH),
                                new KeyValue(pointNode.scaleXProperty(),   1.01, Interpolator.EASE_BOTH)
                            ),
                            new KeyFrame(Duration.millis(560 + delay),
                                new KeyValue(pointNode.scaleYProperty(),   1.0,  Interpolator.EASE_BOTH),
                                new KeyValue(pointNode.scaleXProperty(),   1.0,  Interpolator.EASE_BOTH)
                            )
                        );
                        pointNode.getProperties().put("px.point.intro", barIntro);
                        barIntro.playFromStart();
                    } else {
                        // Data point symbol: pop-in with bounce
                        pointNode.setOpacity(0.0);
                        pointNode.setScaleX(0.0);
                        pointNode.setScaleY(0.0);

                        Timeline pointIntro = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                new KeyValue(pointNode.opacityProperty(),  0.0),
                                new KeyValue(pointNode.scaleXProperty(),   0.0),
                                new KeyValue(pointNode.scaleYProperty(),   0.0)
                            ),
                            new KeyFrame(Duration.millis(200 + delay),
                                new KeyValue(pointNode.opacityProperty(),  1.0,  Interpolator.EASE_OUT),
                                new KeyValue(pointNode.scaleXProperty(),   1.3,  Interpolator.SPLINE(0.12, 0.95, 0.22, 1.0)),
                                new KeyValue(pointNode.scaleYProperty(),   1.3,  Interpolator.SPLINE(0.12, 0.95, 0.22, 1.0))
                            ),
                            new KeyFrame(Duration.millis(310 + delay),
                                new KeyValue(pointNode.scaleXProperty(),   0.88, Interpolator.EASE_BOTH),
                                new KeyValue(pointNode.scaleYProperty(),   0.88, Interpolator.EASE_BOTH)
                            ),
                            new KeyFrame(Duration.millis(400 + delay),
                                new KeyValue(pointNode.scaleXProperty(),   1.0,  Interpolator.EASE_BOTH),
                                new KeyValue(pointNode.scaleYProperty(),   1.0,  Interpolator.EASE_BOTH)
                            )
                        );
                        pointNode.getProperties().put("px.point.intro", pointIntro);
                        pointIntro.playFromStart();

                        // After entrance — install continuous float loop
                        pointIntro.setOnFinished(e -> installPointFloatLoop(pointNode, delay));
                    }
                    pointIndex++;
                }
                seriesIndex++;
            }
        }
    }

    /** Gentle perpetual float for line chart data-point symbols. */
    private static void installPointFloatLoop(Node node, double phaseOffset) {
        if (node == null) return;
        Object existing = node.getProperties().get("px.point.float");
        if (existing instanceof Animation a) a.stop();

        TranslateTransition tt = new TranslateTransition(Duration.millis(1600 + RNG.nextInt(400)), node);
        tt.setFromY(0);
        tt.setToY(-4.5);
        tt.setAutoReverse(true);
        tt.setCycleCount(Animation.INDEFINITE);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.setDelay(Duration.millis(phaseOffset % 600));
        node.getProperties().put("px.point.float", tt);
        tt.play();
    }

    /**
     * Animate stat count-up for overview cards (lblActivePcs, lblIncomeToday, etc.)
     * Rolls the displayed number from 0 up to the target value over ~700ms.
     */
    public static void animateCountUp(javafx.scene.control.Label label, double targetValue, String suffix, String format) {
        if (label == null) return;
        Object prev = label.getProperties().get("px.countup");
        if (prev instanceof Animation a) a.stop();

        final long steps = 28;
        final long stepMs = 25;
        final long totalMs = steps * stepMs;
        Timeline tl = new Timeline();
        for (int i = 1; i <= steps; i++) {
            double frac = (double) i / steps;
            // Ease-out cubic
            double eased = 1.0 - Math.pow(1.0 - frac, 3.0);
            double displayed = targetValue * eased;
            final double d = displayed;
            final int step = i;
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(stepMs * step), e -> {
                if (format != null && !format.isEmpty()) {
                    label.setText(String.format(format, d) + (suffix != null ? suffix : ""));
                } else {
                    label.setText((int) d + (suffix != null ? suffix : ""));
                }
            }));
        }
        label.getProperties().put("px.countup", tl);
        tl.play();
    }

    /**
     * Flash-highlight a stat card (VBox) when its value changes.
     * A neon green rim briefly pulses around the card border.
     */
    public static void flashStatCard(javafx.scene.layout.Region card) {
        if (card == null) return;
        String base = card.getStyle();
        Timeline flash = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(card.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(80),
                new KeyValue(card.opacityProperty(), 0.6)),
            new KeyFrame(Duration.millis(200),
                new KeyValue(card.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(280),
                new KeyValue(card.opacityProperty(), 0.75)),
            new KeyFrame(Duration.millis(400),
                new KeyValue(card.opacityProperty(), 1.0))
        );
        flash.play();
    }


    private static void installOverviewTitleEffects(
            Label lblOverviewTitle,
            Label lblTopSellTitle,
            Label lblPcStatusTitle,
            Label lblIncomeTitle,
            Label lblPeakTitle
    ) {
        List<Label> titles = Arrays.asList(
                lblOverviewTitle,
                lblTopSellTitle,
                lblPcStatusTitle,
                lblIncomeTitle,
                lblPeakTitle
        );

        long delay = 0;
        for (Label label : titles) {
            if (label == null) continue;

            Timeline delayed = new Timeline(new KeyFrame(Duration.millis(delay), e -> {
                GlitchFX.hover(label);
                Animation loop = GlitchFX.loop(label, 3200 + RNG.nextInt(1400));
                if (loop != null) {
                    label.getProperties().put("overview.glitch.loop", loop);
                }
            }));
            delayed.play();

            delay += 160;
        }
    }

    private static void installOverviewCardEntryAnimations(Node... nodes) {
        int i = 0;
        for (Node node : nodes) {
            if (node == null) continue;

            node.setOpacity(0);
            node.setTranslateY(26);
            node.setScaleX(0.96);
            node.setScaleY(0.96);

            FadeTransition fade = new FadeTransition(Duration.millis(320), node);
            fade.setFromValue(0);
            fade.setToValue(1);

            TranslateTransition slide = new TranslateTransition(Duration.millis(420), node);
            slide.setFromY(26);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            ScaleTransition pop = new ScaleTransition(Duration.millis(420), node);
            pop.setFromX(0.96);
            pop.setFromY(0.96);
            pop.setToX(1);
            pop.setToY(1);
            pop.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition group = new ParallelTransition(fade, slide, pop);
            SequentialTransition seq = new SequentialTransition(
                    new PauseTransition(Duration.millis(i * 90L)),
                    group
            );
            seq.play();
            i++;
        }
    }

    private static void installOverviewHoverPulse(Node node, boolean glitchOnClick) {
        if (node == null) return;

        ScaleTransition enter = new ScaleTransition(Duration.millis(180), node);
        enter.setToX(1.02);
        enter.setToY(1.02);
        enter.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition lift = new TranslateTransition(Duration.millis(180), node);
        lift.setToY(-3);
        lift.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition exit = new ScaleTransition(Duration.millis(180), node);
        exit.setToX(1.0);
        exit.setToY(1.0);
        exit.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition down = new TranslateTransition(Duration.millis(180), node);
        down.setToY(0);
        down.setInterpolator(Interpolator.EASE_OUT);

        node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            enter.playFromStart();
            lift.playFromStart();
        });

        node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            exit.playFromStart();
            down.playFromStart();
        });

        node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
            if (glitchOnClick) {
                GlitchFX.burst(node);
            }
        });
    }

    private static void installOverviewSparkleLayer(StackPane holder, int count) {
        if (holder == null) return;

        Pane overlay = new Pane();
        overlay.setMouseTransparent(true);
        overlay.getStyleClass().add("sparkle-layer");
        holder.getChildren().add(overlay);
        StackPane.setAlignment(overlay, Pos.CENTER);

        holder.layoutBoundsProperty().addListener((obs, oldV, newV) -> {
            overlay.setPrefSize(newV.getWidth(), newV.getHeight());
        });

        overlay.setPrefSize(holder.getWidth(), holder.getHeight());

        for (int i = 0; i < count; i++) {
            Circle star = new Circle(1.3 + RNG.nextDouble() * 1.8);
            star.setManaged(false);
            star.setFill(i % 2 == 0 ? Color.web("#7de9ff") : Color.web("#ffd1ee"));
            star.setOpacity(0.0);
            overlay.getChildren().add(star);

            playOverviewSparkle(star, overlay, i * 210.0);
        }
    }

    private static void playOverviewSparkle(Circle star, Pane overlay, double delayMillis) {
        double width = Math.max(overlay.getPrefWidth(), 200);
        double height = Math.max(overlay.getPrefHeight(), 120);

        star.setLayoutX(10 + RNG.nextDouble() * (width - 20));
        star.setLayoutY(10 + RNG.nextDouble() * (height - 20));

        Timeline twinkle = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(star.opacityProperty(), 0.0),
                        new KeyValue(star.scaleXProperty(), 0.2),
                        new KeyValue(star.scaleYProperty(), 0.2)
                ),
                new KeyFrame(Duration.millis(350),
                        new KeyValue(star.opacityProperty(), 0.95),
                        new KeyValue(star.scaleXProperty(), 1.5),
                        new KeyValue(star.scaleYProperty(), 1.5)
                ),
                new KeyFrame(Duration.millis(760),
                        new KeyValue(star.opacityProperty(), 0.0),
                        new KeyValue(star.scaleXProperty(), 0.25),
                        new KeyValue(star.scaleYProperty(), 0.25)
                )
        );

        twinkle.setDelay(Duration.millis(delayMillis));
        twinkle.setOnFinished(e -> playOverviewSparkle(star, overlay, 900 + RNG.nextInt(1800)));
        twinkle.play();
    }

    private static void installOverviewSlicePulse(Node node, double delayMillis) {
        if (node == null) return;

        // Stop any prior pulse
        Object existing = node.getProperties().get("px.slice.pulse");
        if (existing instanceof Animation a) { a.stop(); }

        // Breathing pulse: scale + opacity shimmer
        Timeline pulse = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(node.scaleXProperty(),   1.0,  Interpolator.EASE_BOTH),
                new KeyValue(node.scaleYProperty(),   1.0,  Interpolator.EASE_BOTH),
                new KeyValue(node.opacityProperty(),  1.0,  Interpolator.EASE_BOTH)
            ),
            new KeyFrame(Duration.millis(1200),
                new KeyValue(node.scaleXProperty(),   1.05, Interpolator.EASE_BOTH),
                new KeyValue(node.scaleYProperty(),   1.05, Interpolator.EASE_BOTH),
                new KeyValue(node.opacityProperty(),  0.85, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(Duration.millis(2400),
                new KeyValue(node.scaleXProperty(),   1.0,  Interpolator.EASE_BOTH),
                new KeyValue(node.scaleYProperty(),   1.0,  Interpolator.EASE_BOTH),
                new KeyValue(node.opacityProperty(),  1.0,  Interpolator.EASE_BOTH)
            )
        );
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setDelay(Duration.millis(delayMillis));
        node.getProperties().put("px.slice.pulse", pulse);
        pulse.play();
    }
    public static void collapseSidebarInstant(javafx.scene.layout.Region sidebar) {
    setSidebarCollapsed(sidebar, true, false);
}

    public static void installInventoryCardMotion(Node card) {
        if (card == null) return;
        if (Boolean.TRUE.equals(card.getProperties().get("px.inventory.card.motion"))) return;
        card.getProperties().put("px.inventory.card.motion", true);

        final ScaleTransition hoverIn = new ScaleTransition(Duration.millis(150), card);
        hoverIn.setInterpolator(Interpolator.EASE_BOTH);
        final ScaleTransition hoverOut = new ScaleTransition(Duration.millis(150), card);
        hoverOut.setInterpolator(Interpolator.EASE_BOTH);

        card.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            hoverIn.stop();
            hoverIn.setToX(1.035);
            hoverIn.setToY(1.035);
            hoverIn.playFromStart();
        });

        card.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            hoverOut.stop();
            hoverOut.setToX(1.0);
            hoverOut.setToY(1.0);
            hoverOut.playFromStart();
        });
    }

    public static void playInventoryCardSpawn(Node card, int index) {
        if (card == null) return;
        // Skip if already fully visible — prevents pop-in re-trigger on data refresh
        if (Boolean.TRUE.equals(card.getProperties().get("px.motion.entrance"))
                && card.getOpacity() >= 1.0) return;

        // Mark immediately so playEntrance() skips this card and never resets it
        card.getProperties().put("px.motion.entrance", true);

        double delay = Math.min(index, 8) * 18.0; // reduced from 34ms for snappier feel
        card.setOpacity(0.0);
        card.setTranslateY(20.0); // reduced from 26px
        card.setScaleX(0.93);
        card.setScaleY(0.93);

        // Cache card during animation to reduce GPU cost
        card.setCache(true);
        card.setCacheHint(javafx.scene.CacheHint.SPEED);

        FadeTransition ft = new FadeTransition(Duration.millis(220), card);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.setDelay(Duration.millis(delay));
        ft.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition tt = new TranslateTransition(Duration.millis(280), card);
        tt.setFromY(20.0);
        tt.setToY(0.0);
        tt.setDelay(Duration.millis(delay));
        tt.setInterpolator(Interpolator.SPLINE(0.2, 0.9, 0.2, 1.0));

        ScaleTransition st = new ScaleTransition(Duration.millis(280), card);
        st.setFromX(0.93);
        st.setFromY(0.93);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setDelay(Duration.millis(delay));
        st.setInterpolator(Interpolator.SPLINE(0.2, 0.9, 0.2, 1.0));

        ParallelTransition anim = new ParallelTransition(ft, tt, st);
        anim.setOnFinished(e -> {
            card.setCache(false);
            card.setCacheHint(javafx.scene.CacheHint.DEFAULT);
        });
        anim.playFromStart();
    }

    public static void playInventoryCardAdded(Node card) {
        if (card == null) return;

        card.setOpacity(0.0);
        card.setTranslateY(34.0);
        card.setScaleX(0.82);
        card.setScaleY(0.82);

        FadeTransition ft = new FadeTransition(Duration.millis(280), card);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition tt = new TranslateTransition(Duration.millis(360), card);
        tt.setFromY(34.0);
        tt.setToY(0.0);
        tt.setInterpolator(Interpolator.SPLINE(0.15, 0.88, 0.2, 1.0));

        ScaleTransition st = new ScaleTransition(Duration.millis(360), card);
        st.setFromX(0.82);
        st.setFromY(0.82);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(Interpolator.SPLINE(0.15, 0.88, 0.2, 1.0));

        ScaleTransition pulse = new ScaleTransition(Duration.millis(150), card);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.045);
        pulse.setToY(1.045);
        pulse.setCycleCount(2);
        pulse.setAutoReverse(true);
        pulse.setDelay(Duration.millis(250));

        new ParallelTransition(ft, tt, st, pulse).playFromStart();
    }

    public static void pulseInventoryCard(Node card) {
        if (card == null) return;

        ScaleTransition st = new ScaleTransition(Duration.millis(120), card);
        st.setFromX(card.getScaleX());
        st.setFromY(card.getScaleY());
        st.setToX(card.getScaleX() + 0.03);
        st.setToY(card.getScaleY() + 0.03);
        st.setCycleCount(2);
        st.setAutoReverse(true);
        st.playFromStart();
    }



    public static void installNotificationItemHoverMotion(Node itemRoot) {
        if (itemRoot == null) return;
        itemRoot.setScaleX(1.0);
        itemRoot.setScaleY(1.0);
        itemRoot.setTranslateX(0.0);

        final TranslateTransition hoverIn = new TranslateTransition(Duration.millis(130), itemRoot);
        hoverIn.setToX(4.0);
        hoverIn.setInterpolator(Interpolator.EASE_BOTH);

        final TranslateTransition hoverOut = new TranslateTransition(Duration.millis(130), itemRoot);
        hoverOut.setToX(0.0);
        hoverOut.setInterpolator(Interpolator.EASE_BOTH);

        itemRoot.setOnMouseEntered(e -> {
            hoverOut.stop();
            hoverIn.playFromStart();
        });
        itemRoot.setOnMouseExited(e -> {
            hoverIn.stop();
            hoverOut.playFromStart();
        });
    }

    public static void animateNotificationItemDismiss(Node itemRoot, Runnable onFinished) {
        if (itemRoot == null) {
            if (onFinished != null) onFinished.run();
            return;
        }

        FadeTransition fade = new FadeTransition(Duration.millis(150), itemRoot);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(170), itemRoot);
        slide.setFromX(itemRoot.getTranslateX());
        slide.setToX(24.0);
        slide.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(170), itemRoot);
        shrink.setFromX(itemRoot.getScaleX());
        shrink.setFromY(itemRoot.getScaleY());
        shrink.setToX(0.96);
        shrink.setToY(0.96);
        shrink.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition out = new ParallelTransition(fade, slide, shrink);
        out.setOnFinished(e -> {
            if (onFinished != null) onFinished.run();
        });
        out.playFromStart();
    }

    public static void installPcCardHoverMotion(Node rootCard) {
        if (rootCard == null) return;
        if (Boolean.TRUE.equals(rootCard.getProperties().get("px.pc.card.hover"))) return;
        rootCard.getProperties().put("px.pc.card.hover", true);

        final ScaleTransition hoverIn = new ScaleTransition(Duration.millis(160), rootCard);
        hoverIn.setToX(1.025);
        hoverIn.setToY(1.025);
        hoverIn.setInterpolator(Interpolator.EASE_OUT);

        final TranslateTransition liftIn = new TranslateTransition(Duration.millis(160), rootCard);
        liftIn.setToY(-4.0);
        liftIn.setInterpolator(Interpolator.EASE_OUT);

        final ScaleTransition hoverOut = new ScaleTransition(Duration.millis(160), rootCard);
        hoverOut.setToX(1.0);
        hoverOut.setToY(1.0);
        hoverOut.setInterpolator(Interpolator.EASE_OUT);

        final TranslateTransition liftOut = new TranslateTransition(Duration.millis(160), rootCard);
        liftOut.setToY(0.0);
        liftOut.setInterpolator(Interpolator.EASE_OUT);

        rootCard.setOnMouseEntered(e -> {
            hoverOut.stop();
            liftOut.stop();
            new ParallelTransition(hoverIn, liftIn).playFromStart();
        });
        rootCard.setOnMouseExited(e -> {
            hoverIn.stop();
            liftIn.stop();
            new ParallelTransition(hoverOut, liftOut).playFromStart();
        });
    }

    public static void installPcStatusPulse(Node statusDot, String status) {
        if (statusDot == null) return;

        Object existing = statusDot.getProperties().get("px.pc.status.pulse");
        if (existing instanceof Animation) {
            ((Animation) existing).stop();
        }

        if ("Offline".equals(status) || "Maintenance".equals(status)) {
            statusDot.setScaleX(1.0);
            statusDot.setScaleY(1.0);
            statusDot.getProperties().remove("px.pc.status.pulse");
            return;
        }

        ScaleTransition pulse1 = new ScaleTransition(Duration.millis(820), statusDot);
        pulse1.setToX(1.22);
        pulse1.setToY(1.22);
        pulse1.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition pulse2 = new ScaleTransition(Duration.millis(820), statusDot);
        pulse2.setToX(1.0);
        pulse2.setToY(1.0);
        pulse2.setInterpolator(Interpolator.EASE_BOTH);

        SequentialTransition seq = new SequentialTransition(pulse1, pulse2);
        seq.setCycleCount(Animation.INDEFINITE);
        statusDot.getProperties().put("px.pc.status.pulse", seq);
        seq.play();
    }

    public static void playPcGridCardSpawn(Node card, int index) {
        if (card == null) return;
        // Skip if already fully visible (e.g. a live-update reload of an existing card).
        // This prevents the pop-in re-trigger when loadPC() is called again from RMI callbacks.
        if (Boolean.TRUE.equals(card.getProperties().get("px.motion.entrance"))
                && card.getOpacity() >= 1.0) return;

        // Mark immediately so playEntrance() skips this card
        card.getProperties().put("px.motion.entrance", true);
        card.setOpacity(0.0);
        card.setTranslateY(16.0);

        // Cache card during animation to reduce rendering cost
        card.setCache(true);
        card.setCacheHint(javafx.scene.CacheHint.SPEED);

        Duration delay = Duration.millis(index * 16L); // reduced from 24ms for snappier feel

        FadeTransition fade = new FadeTransition(Duration.millis(200), card);
        fade.setToValue(1.0);
        fade.setDelay(delay);

        TranslateTransition slide = new TranslateTransition(Duration.millis(260), card);
        slide.setToY(0.0);
        slide.setDelay(delay);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition anim = new ParallelTransition(fade, slide);
        anim.setOnFinished(e -> {
            card.setCache(false);
            card.setCacheHint(javafx.scene.CacheHint.DEFAULT);
        });
        anim.playFromStart();
    }

    public static void installLiveStatPulse(Node card, int delayMs) {
        if (card == null) return;
        Object existing = card.getProperties().get("px.live.stat.pulse");
        if (existing instanceof Animation) {
            ((Animation) existing).stop();
        }

        ScaleTransition up = new ScaleTransition(Duration.millis(900), card);
        up.setToX(1.012);
        up.setToY(1.012);
        up.setDelay(Duration.millis(delayMs));

        ScaleTransition down = new ScaleTransition(Duration.millis(900), card);
        down.setToX(1.0);
        down.setToY(1.0);

        SequentialTransition seq = new SequentialTransition(up, down);
        seq.setCycleCount(Animation.INDEFINITE);
        card.getProperties().put("px.live.stat.pulse", seq);
        seq.play();
    }

    public static void pulseSelectionCard(Node card) {
        if (card == null) return;
        ScaleTransition pop = new ScaleTransition(Duration.millis(180), card);
        pop.setFromX(0.985);
        pop.setFromY(0.985);
        pop.setToX(1.0);
        pop.setToY(1.0);
        pop.playFromStart();
    }


    public static void openPopupLayer(javafx.scene.layout.StackPane overlay, Node modalCard) {
        if (overlay == null || modalCard == null) return;
        overlay.setManaged(true);
        overlay.setVisible(true);
        overlay.setMouseTransparent(false);
        modalCard.setManaged(true);
        modalCard.setVisible(true);
        overlay.toFront();
        modalCard.toFront();
        NovaFX.showPopup(overlay, modalCard);
    }

    public static void closePopupLayer(javafx.scene.layout.StackPane overlay, Node modalCard, Runnable onFinished) {
        if (overlay == null || modalCard == null) {
            if (onFinished != null) onFinished.run();
            return;
        }
        NovaFX.hidePopup(overlay, modalCard, () -> {
            overlay.setVisible(false);
            overlay.setManaged(false);
            overlay.setMouseTransparent(true);
            modalCard.setVisible(false);
            modalCard.setManaged(false);
            modalCard.setMouseTransparent(false);
            if (onFinished != null) onFinished.run();
        });
    }

    public static void installPopupBackdropClose(javafx.scene.layout.StackPane overlay, Node modalCard, Runnable onClose) {
        if (overlay == null) return;
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay && onClose != null) onClose.run();
        });
        if (modalCard != null) {
            modalCard.setOnMouseClicked(e -> e.consume());
        }
    }

    public static void openDrawerLayer(javafx.scene.layout.StackPane overlay, Node drawerCard) {
        if (overlay == null || drawerCard == null) return;
        overlay.setManaged(true);
        overlay.setVisible(true);
        overlay.setMouseTransparent(false);
        drawerCard.setManaged(true);
        drawerCard.setVisible(true);
        overlay.toFront();
        drawerCard.toFront();
        NovaFX.showDrawer(overlay, drawerCard);
    }

    public static void closeDrawerLayer(javafx.scene.layout.StackPane overlay, Node drawerCard, Runnable onFinished) {
        if (overlay == null || drawerCard == null) {
            if (onFinished != null) onFinished.run();
            return;
        }
        NovaFX.hideDrawer(overlay, drawerCard, () -> {
            overlay.setVisible(false);
            overlay.setManaged(false);
            overlay.setMouseTransparent(true);
            drawerCard.setVisible(false);
            drawerCard.setManaged(false);
            drawerCard.setMouseTransparent(false);
            drawerCard.setRotate(0.0);
            if (onFinished != null) onFinished.run();
        });
    }

    public static void prepareModal(Node overlay, Node modalCard) {
        if (overlay == null || modalCard == null) return;
        overlay.setOpacity(0.0);
        modalCard.setOpacity(0.0);
        modalCard.setScaleX(0.96);
        modalCard.setScaleY(0.96);
        modalCard.setTranslateY(18.0);
    }

    public static void playModalOpen(Node overlay, Node modalCard) {
        if (overlay == null || modalCard == null) return;

        FadeTransition bg = new FadeTransition(Duration.millis(170), overlay);
        bg.setToValue(1.0);

        FadeTransition fade = new FadeTransition(Duration.millis(210), modalCard);
        fade.setToValue(1.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(210), modalCard);
        scale.setToX(1.0);
        scale.setToY(1.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(220), modalCard);
        slide.setToY(0.0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(bg, fade, scale, slide).playFromStart();
    }

    public static void playModalClose(Node overlay, Node modalCard, Runnable onFinished) {
        if (overlay == null || modalCard == null) {
            if (onFinished != null) onFinished.run();
            return;
        }

        FadeTransition bg = new FadeTransition(Duration.millis(150), overlay);
        bg.setToValue(0.0);
        FadeTransition fade = new FadeTransition(Duration.millis(150), modalCard);
        fade.setToValue(0.0);
        ScaleTransition scale = new ScaleTransition(Duration.millis(150), modalCard);
        scale.setToX(0.96);
        scale.setToY(0.96);
        TranslateTransition slide = new TranslateTransition(Duration.millis(150), modalCard);
        slide.setToY(16.0);
        ParallelTransition out = new ParallelTransition(bg, fade, scale, slide);
        out.setOnFinished(e -> {
            if (onFinished != null) onFinished.run();
        });
        out.playFromStart();
    }

    public static void prepareSideModal(Node overlay, Node panel) {
        if (overlay == null || panel == null) return;
        overlay.setOpacity(0.0);
        panel.setOpacity(0.0);
        panel.setTranslateX(72.0);
        panel.setScaleX(0.985);
        panel.setScaleY(0.985);
    }

    public static void playSideModalOpen(Node overlay, Node panel) {
        if (overlay == null || panel == null) return;

        FadeTransition bg = new FadeTransition(Duration.millis(170), overlay);
        bg.setToValue(1.0);

        FadeTransition fade = new FadeTransition(Duration.millis(240), panel);
        fade.setToValue(1.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(320), panel);
        slide.setToX(0.0);
        slide.setInterpolator(Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0));

        ScaleTransition scale = new ScaleTransition(Duration.millis(240), panel);
        scale.setToX(1.0);
        scale.setToY(1.0);

        new ParallelTransition(bg, fade, slide, scale).playFromStart();
    }

    public static void playSideModalClose(Node overlay, Node panel, Runnable onFinished) {
        if (overlay == null || panel == null) {
            if (onFinished != null) onFinished.run();
            return;
        }

        FadeTransition bg = new FadeTransition(Duration.millis(160), overlay);
        bg.setToValue(0.0);

        FadeTransition fade = new FadeTransition(Duration.millis(160), panel);
        fade.setToValue(0.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(200), panel);
        slide.setToX(56.0);
        slide.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale = new ScaleTransition(Duration.millis(160), panel);
        scale.setToX(0.985);
        scale.setToY(0.985);

        ParallelTransition out = new ParallelTransition(bg, fade, slide, scale);
        out.setOnFinished(e -> {
            if (onFinished != null) onFinished.run();
        });
        out.playFromStart();
    }

    public static void playNeonPulse(Node node) {
        if (node == null) return;
        ScaleTransition up = new ScaleTransition(Duration.millis(180), node);
        up.setFromX(0.985);
        up.setFromY(0.985);
        up.setToX(1.0);
        up.setToY(1.0);
        up.setInterpolator(Interpolator.EASE_OUT);
        up.playFromStart();
    }

    public static void animateRegionPrefWidth(javafx.scene.layout.Region region, double targetWidth) {
        if (region == null || targetWidth <= 0) return;
        Object running = region.getProperties().get("px.region.width.anim");
        if (running instanceof Animation) {
            ((Animation) running).stop();
        }

        double start = region.getPrefWidth();
        if (start <= 0 || Double.isNaN(start)) {
            start = region.getWidth();
        }
        if (start <= 0 || Double.isNaN(start)) {
            start = targetWidth;
        }

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(region.prefWidthProperty(), start, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(160),
                        new KeyValue(region.prefWidthProperty(), targetWidth, Interpolator.SPLINE(0.22, 0.88, 0.24, 1.0)))
        );
        tl.setOnFinished(e -> {
            region.setPrefWidth(targetWidth);
            region.getProperties().remove("px.region.width.anim");
        });
        region.getProperties().put("px.region.width.anim", tl);
        tl.playFromStart();
    }

    public static void installGameCardMotion(Node card) {
        installInventoryCardMotion(card);
    }

    public static void playGameCardSpawn(Node card, int index) {
        playInventoryCardSpawn(card, index);
    }

    public static void playGameCardAdded(Node card) {
        playInventoryCardAdded(card);
    }

    public static void pulseGameCard(Node card) {
        pulseInventoryCard(card);
    }

    public static void playModalEntrance(Node modal) {
        if (modal == null) return;
        modal.setOpacity(0.0);
        modal.setTranslateY(20.0);
        modal.setScaleX(0.94);
        modal.setScaleY(0.94);

        FadeTransition ft = new FadeTransition(Duration.millis(180), modal);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition tt = new TranslateTransition(Duration.millis(220), modal);
        tt.setFromY(20.0);
        tt.setToY(0.0);
        tt.setInterpolator(Interpolator.SPLINE(0.2, 0.9, 0.2, 1.0));

        ScaleTransition st = new ScaleTransition(Duration.millis(220), modal);
        st.setFromX(0.94);
        st.setFromY(0.94);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(Interpolator.SPLINE(0.2, 0.9, 0.2, 1.0));

        new ParallelTransition(ft, tt, st).playFromStart();
    }


    public static void applyUltraCardHover(javafx.scene.Node card) {
    if (card == null) return;
    if (Boolean.TRUE.equals(card.getProperties().get("px.ultra.card.hover"))) return;
    card.getProperties().put("px.ultra.card.hover", true);

    final javafx.scene.effect.Effect baseEffect = card.getEffect();

    javafx.scene.effect.DropShadow hoverGlow = new javafx.scene.effect.DropShadow();
    hoverGlow.setBlurType(javafx.scene.effect.BlurType.GAUSSIAN);
    hoverGlow.setRadius(22);
    hoverGlow.setSpread(0.12);
    hoverGlow.setOffsetY(0);
    hoverGlow.setColor(javafx.scene.paint.Color.rgb(72, 222, 255, 0.24));

    javafx.scene.effect.DropShadow accentGlow = new javafx.scene.effect.DropShadow();
    accentGlow.setBlurType(javafx.scene.effect.BlurType.GAUSSIAN);
    accentGlow.setRadius(14);
    accentGlow.setSpread(0.08);
    accentGlow.setOffsetY(0);
    accentGlow.setColor(javafx.scene.paint.Color.rgb(255, 102, 214, 0.16));
    accentGlow.setInput(hoverGlow);

    // REUSE animations (IMPORTANT FIX)
    javafx.animation.ScaleTransition scaleUp = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(190), card);
    scaleUp.setToX(1.028);
    scaleUp.setToY(1.028);
    scaleUp.setInterpolator(javafx.animation.Interpolator.SPLINE(0.16, 0.92, 0.22, 1.0));

    javafx.animation.TranslateTransition liftUp = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(190), card);
    liftUp.setToY(-7.5);
    liftUp.setInterpolator(javafx.animation.Interpolator.SPLINE(0.16, 0.92, 0.22, 1.0));

    javafx.animation.RotateTransition rotateUp = new javafx.animation.RotateTransition(javafx.util.Duration.millis(190), card);
    rotateUp.setToAngle(-0.45);
    rotateUp.setInterpolator(javafx.animation.Interpolator.SPLINE(0.16, 0.92, 0.22, 1.0));

    javafx.animation.ScaleTransition scaleDown = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(170), card);
    scaleDown.setToX(1.0);
    scaleDown.setToY(1.0);

    javafx.animation.TranslateTransition liftDown = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(170), card);
    liftDown.setToY(0.0);

    javafx.animation.RotateTransition rotateDown = new javafx.animation.RotateTransition(javafx.util.Duration.millis(170), card);
    rotateDown.setToAngle(0.0);

    final javafx.animation.ParallelTransition[] currentAnim = new javafx.animation.ParallelTransition[1];

    card.setOnMouseEntered(e -> {
        if (currentAnim[0] != null) currentAnim[0].stop();

        card.setEffect(accentGlow);

        currentAnim[0] = new javafx.animation.ParallelTransition(
                scaleUp, liftUp, rotateUp
        );
        currentAnim[0].playFromStart();
    });

    card.setOnMouseExited(e -> {
        if (currentAnim[0] != null) currentAnim[0].stop();

        card.setEffect(baseEffect);

        currentAnim[0] = new javafx.animation.ParallelTransition(
                scaleDown, liftDown, rotateDown
        );
        currentAnim[0].playFromStart();
    });
}

    public static void applyCardHover(javafx.scene.Node card) {

        javafx.animation.ScaleTransition scaleUp = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(180), card);
        scaleUp.setToX(1.05);
        scaleUp.setToY(1.05);

        javafx.animation.TranslateTransition liftUp = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(180), card);
        liftUp.setToY(-6);

        javafx.animation.ParallelTransition hoverIn = new javafx.animation.ParallelTransition(scaleUp, liftUp);

        javafx.animation.ScaleTransition scaleDown = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(180), card);
        scaleDown.setToX(1);
        scaleDown.setToY(1);

        javafx.animation.TranslateTransition liftDown = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(180), card);
        liftDown.setToY(0);

        javafx.animation.ParallelTransition hoverOut = new javafx.animation.ParallelTransition(scaleDown, liftDown);

        card.setOnMouseEntered(e -> hoverIn.playFromStart());
        card.setOnMouseExited(e -> hoverOut.playFromStart());
    }


    public static void applyToLater(Parent root) {
        if (root == null) return;
        Platform.runLater(() -> applyTo(root));
    }

    public static Scene createTransparentScene(Parent root) {
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        return scene;
    }

    public static void enhanceModalStage(Stage stage, Parent root, boolean drawerStyle) {
        if (stage == null || root == null) return;
        stage.setScene(createTransparentScene(root));
        stage.setResizable(false);
        stage.setOnShown(e -> {
            applyTo(root);
            playWindowIntro(root, drawerStyle);
        });
    }

    public static void playWindowIntro(Node root, boolean drawerStyle) {
        if (root == null) return;

        root.setOpacity(0.0);
        root.setScaleX(drawerStyle ? 0.972 : 0.92);
        root.setScaleY(drawerStyle ? 0.972 : 0.92);
        root.setTranslateX(drawerStyle ? 88.0 : 0.0);
        root.setTranslateY(drawerStyle ? 0.0 : 28.0);

        FadeTransition fade = new FadeTransition(Duration.millis(drawerStyle ? 280 : 220), root);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition settle1 = new TranslateTransition(Duration.millis(drawerStyle ? 360 : 260), root);
        settle1.setFromX(drawerStyle ? 88.0 : 0.0);
        settle1.setFromY(drawerStyle ? 0.0 : 28.0);
        settle1.setToX(drawerStyle ? -10.0 : 0.0);
        settle1.setToY(drawerStyle ? 0.0 : -4.0);
        settle1.setInterpolator(Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0));

        TranslateTransition settle2 = new TranslateTransition(Duration.millis(drawerStyle ? 150 : 130), root);
        settle2.setFromX(drawerStyle ? -10.0 : 0.0);
        settle2.setFromY(drawerStyle ? 0.0 : -4.0);
        settle2.setToX(0.0);
        settle2.setToY(0.0);
        settle2.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale1 = new ScaleTransition(Duration.millis(drawerStyle ? 320 : 250), root);
        scale1.setFromX(drawerStyle ? 0.972 : 0.92);
        scale1.setFromY(drawerStyle ? 0.972 : 0.92);
        scale1.setToX(drawerStyle ? 1.012 : 1.01);
        scale1.setToY(drawerStyle ? 1.012 : 1.01);
        scale1.setInterpolator(Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0));

        ScaleTransition scale2 = new ScaleTransition(Duration.millis(150), root);
        scale2.setFromX(drawerStyle ? 1.012 : 1.01);
        scale2.setFromY(drawerStyle ? 1.012 : 1.01);
        scale2.setToX(1.0);
        scale2.setToY(1.0);
        scale2.setInterpolator(Interpolator.EASE_BOTH);

        SequentialTransition slide = new SequentialTransition(settle1, settle2);
        SequentialTransition scale = new SequentialTransition(scale1, scale2);
        new ParallelTransition(fade, slide, scale).playFromStart();
    }

    public static void showOverlayInStack(javafx.scene.layout.StackPane hostStack, Parent modalRoot, boolean drawerStyle) {
        showOverlayInStack(hostStack, modalRoot, drawerStyle, null);
    }

    public static void showOverlayInStack(javafx.scene.layout.StackPane hostStack, Parent modalRoot, boolean drawerStyle, Runnable onClosed) {
        if (hostStack == null || modalRoot == null) return;

        List<Node> blurredNodes = hostStack.getChildren().stream()
                .filter(node -> node != null && node.isVisible())
                .collect(Collectors.toList());
        Map<Node, Effect> oldEffects = new IdentityHashMap<>();
        Map<Node, Double> oldOpacities = new IdentityHashMap<>();
        for (Node node : blurredNodes) {
            oldEffects.put(node, node.getEffect());
            oldOpacities.put(node, node.getOpacity());
            node.setEffect(new GaussianBlur(0));
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("content-stack-modal-overlay");
        overlay.setPickOnBounds(true);
        overlay.setOpacity(0.0);
        overlay.setManaged(true);
        overlay.setVisible(true);
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        overlay.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(6,10,22,0.08), rgba(8,14,28,0.24));");

        normalizeOverlayShellSize(modalRoot);
        Node animatedNode = resolveAnimatedModalNode(modalRoot);

        StackPane.setAlignment(modalRoot, drawerStyle ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER);
        overlay.getChildren().add(modalRoot);
        overlay.getProperties().put("px.overlay.content", Boolean.TRUE);
        overlay.getProperties().put("px.overlay.root", modalRoot);
        overlay.getProperties().put("px.overlay.card", animatedNode);
        overlay.getProperties().put("px.overlay.blurredNodes", blurredNodes);
        overlay.getProperties().put("px.overlay.oldEffects", oldEffects);
        overlay.getProperties().put("px.overlay.oldOpacities", oldOpacities);
        overlay.getProperties().put("px.overlay.drawerStyle", drawerStyle);
        if (onClosed != null) {
            overlay.getProperties().put("px.overlay.onClosed", onClosed);
        }
        modalRoot.getProperties().put("px.overlay.host", overlay);
        animatedNode.getProperties().put("px.overlay.host", overlay);

        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                closeOverlayHost(overlay);
            }
        });

        hostStack.getChildren().add(overlay);
        overlay.toFront();
        applyTo(modalRoot);

        animatedNode.setOpacity(0.0);
        animatedNode.setScaleX(drawerStyle ? 0.968 : 0.88);
        animatedNode.setScaleY(drawerStyle ? 0.968 : 0.88);
        animatedNode.setTranslateX(drawerStyle ? 132.0 : 0.0);
        animatedNode.setTranslateY(drawerStyle ? 0.0 : 54.0);
        animatedNode.setRotate(drawerStyle ? -1.6 : -2.8);

        FadeTransition bg = new FadeTransition(Duration.millis(drawerStyle ? 320 : 260), overlay);
        bg.setFromValue(0.0);
        bg.setToValue(1.0);
        bg.setInterpolator(Interpolator.EASE_OUT);

        Timeline blurIn = new Timeline();
        for (Node node : blurredNodes) {
            if (node.getEffect() instanceof GaussianBlur gb) {
                blurIn.getKeyFrames().addAll(
                        new KeyFrame(Duration.ZERO, new KeyValue(gb.radiusProperty(), 0, Interpolator.EASE_OUT), new KeyValue(node.opacityProperty(), oldOpacities.getOrDefault(node, 1.0), Interpolator.EASE_OUT)),
                        new KeyFrame(Duration.millis(drawerStyle ? 320 : 260), new KeyValue(gb.radiusProperty(), drawerStyle ? 20 : 16, Interpolator.EASE_BOTH), new KeyValue(node.opacityProperty(), Math.max(0.84, oldOpacities.getOrDefault(node, 1.0) - 0.10), Interpolator.EASE_BOTH))
                );
            }
        }

        FadeTransition fade = new FadeTransition(Duration.millis(drawerStyle ? 360 : 280), animatedNode);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide1 = new TranslateTransition(Duration.millis(drawerStyle ? 420 : 340), animatedNode);
        slide1.setFromX(drawerStyle ? 132.0 : 0.0);
        slide1.setFromY(drawerStyle ? 0.0 : 54.0);
        slide1.setToX(drawerStyle ? -18.0 : 0.0);
        slide1.setToY(drawerStyle ? 0.0 : -10.0);
        slide1.setInterpolator(Interpolator.SPLINE(0.12, 0.96, 0.24, 1.0));

        TranslateTransition slide2 = new TranslateTransition(Duration.millis(drawerStyle ? 180 : 170), animatedNode);
        slide2.setFromX(drawerStyle ? -18.0 : 0.0);
        slide2.setFromY(drawerStyle ? 0.0 : -10.0);
        slide2.setToX(drawerStyle ? 4.0 : 0.0);
        slide2.setToY(drawerStyle ? 0.0 : 3.0);
        slide2.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition slide3 = new TranslateTransition(Duration.millis(drawerStyle ? 120 : 110), animatedNode);
        slide3.setFromX(drawerStyle ? 4.0 : 0.0);
        slide3.setFromY(drawerStyle ? 0.0 : 3.0);
        slide3.setToX(0.0);
        slide3.setToY(0.0);
        slide3.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale1 = new ScaleTransition(Duration.millis(drawerStyle ? 360 : 320), animatedNode);
        scale1.setFromX(drawerStyle ? 0.968 : 0.88);
        scale1.setFromY(drawerStyle ? 0.968 : 0.88);
        scale1.setToX(drawerStyle ? 1.016 : 1.028);
        scale1.setToY(drawerStyle ? 1.016 : 1.028);
        scale1.setInterpolator(Interpolator.SPLINE(0.12, 0.96, 0.24, 1.0));

        ScaleTransition scale2 = new ScaleTransition(Duration.millis(160), animatedNode);
        scale2.setFromX(drawerStyle ? 1.016 : 1.028);
        scale2.setFromY(drawerStyle ? 1.016 : 1.028);
        scale2.setToX(0.996);
        scale2.setToY(0.996);
        scale2.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale3 = new ScaleTransition(Duration.millis(110), animatedNode);
        scale3.setFromX(0.996);
        scale3.setFromY(0.996);
        scale3.setToX(1.0);
        scale3.setToY(1.0);
        scale3.setInterpolator(Interpolator.EASE_OUT);

        RotateTransition rotate1 = new RotateTransition(Duration.millis(drawerStyle ? 360 : 300), animatedNode);
        rotate1.setFromAngle(drawerStyle ? -1.6 : -2.8);
        rotate1.setToAngle(drawerStyle ? 0.4 : 0.8);
        rotate1.setInterpolator(Interpolator.SPLINE(0.12, 0.96, 0.24, 1.0));

        RotateTransition rotate2 = new RotateTransition(Duration.millis(120), animatedNode);
        rotate2.setFromAngle(drawerStyle ? 0.4 : 0.8);
        rotate2.setToAngle(0.0);
        rotate2.setInterpolator(Interpolator.EASE_BOTH);

        SequentialTransition slide = new SequentialTransition(slide1, slide2, slide3);
        SequentialTransition scale = new SequentialTransition(scale1, scale2, scale3);
        SequentialTransition rotate = new SequentialTransition(rotate1, rotate2);

        ParallelTransition intro = new ParallelTransition(bg, blurIn, fade, slide, scale, rotate);
        intro.setOnFinished(e -> startModalAura(animatedNode, drawerStyle));
        intro.playFromStart();
    }

    private static void normalizeOverlayShellSize(Parent modalRoot) {
        if (!(modalRoot instanceof Region region)) return;
        region.prefWidthProperty().unbind();
        region.prefHeightProperty().unbind();
        region.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        region.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    }

    private static Node resolveAnimatedModalNode(Parent modalRoot) {
        if (modalRoot instanceof StackPane stack && !stack.getChildren().isEmpty()) {
            for (Node child : stack.getChildren()) {
                if (child == null) continue;
                List<String> classes = child.getStyleClass();
                if (classes.contains("panel") || classes.contains("edit-popup") || classes.contains("nova-modal-card")
                        || classes.contains("cyber-modal-card") || classes.contains("detail-drawer")
                        || classes.contains("pcs-detail-drawer") || classes.contains("customer-modal")
                        || classes.contains("pcs-modal-card")) {
                    normalizeOverlayShellSize(child instanceof Parent ? (Parent) child : null);
                    if (child instanceof Region childRegion) {
                        childRegion.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                    }
                    return child;
                }
            }
            Node fallback = stack.getChildren().get(stack.getChildren().size() - 1);
            if (fallback instanceof Region fallbackRegion) {
                fallbackRegion.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            }
            return fallback;
        }
        return modalRoot;
    }

    private static void startModalAura(Node node, boolean drawerStyle) {
        if (node == null) return;
        Object running = node.getProperties().get("px.modal.aura");
        if (running instanceof Animation animation) {
            animation.stop();
        }

        TranslateTransition hoverY1 = new TranslateTransition(Duration.millis(drawerStyle ? 1900 : 1650), node);
        hoverY1.setToY(drawerStyle ? 0.0 : -2.4);
        hoverY1.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition hoverY2 = new TranslateTransition(Duration.millis(drawerStyle ? 1900 : 1650), node);
        hoverY2.setToY(0.0);
        hoverY2.setInterpolator(Interpolator.EASE_BOTH);

        SequentialTransition drift = new SequentialTransition(hoverY1, hoverY2);
        drift.setCycleCount(Animation.INDEFINITE);
        drift.setAutoReverse(false);
        drift.play();
        node.getProperties().put("px.modal.aura", drift);
    }

    public static void closeOverlayFrom(Node node) {
        if (node == null) return;
        Object host = node.getProperties().get("px.overlay.host");
        if (host instanceof StackPane) {
            closeOverlayHost((StackPane) host);
            return;
        }

        Parent parent = node.getParent();
        while (parent != null) {
            if (parent.getProperties().get("px.overlay.content") instanceof Boolean) {
                closeOverlayHost((StackPane) parent);
                return;
            }
            Object next = parent.getProperties().get("px.overlay.host");
            if (next instanceof StackPane) {
                closeOverlayHost((StackPane) next);
                return;
            }
            parent = parent.getParent();
        }

        javafx.stage.Window window = node.getScene() == null ? null : node.getScene().getWindow();
        if (window instanceof Stage) {
            ((Stage) window).close();
        }
    }

    private static void closeOverlayHost(StackPane overlay) {
        if (overlay == null) return;
        Node card = (Node) overlay.getProperties().get("px.overlay.card");
        if (card == null && !overlay.getChildren().isEmpty()) {
            card = overlay.getChildren().get(0);
        }
        final Node modalNode = card;

        boolean drawerStyle = Boolean.TRUE.equals(overlay.getProperties().get("px.overlay.drawerStyle"));

        FadeTransition bg = new FadeTransition(Duration.millis(190), overlay);
        bg.setToValue(0.0);
        bg.setInterpolator(Interpolator.EASE_BOTH);

        Object aura = modalNode == null ? null : modalNode.getProperties().get("px.modal.aura");
        if (aura instanceof Animation animation) {
            animation.stop();
            if (modalNode.getProperties() != null) {
                modalNode.getProperties().remove("px.modal.aura");
            }
        }

        FadeTransition fade = new FadeTransition(Duration.millis(180), modalNode);
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_IN);

        TranslateTransition slide = new TranslateTransition(Duration.millis(240), modalNode);
        slide.setToX(drawerStyle ? 104.0 : 0.0);
        slide.setToY(drawerStyle ? 0.0 : 36.0);
        slide.setInterpolator(Interpolator.SPLINE(0.4, 0.0, 0.8, 0.6));

        ScaleTransition scale = new ScaleTransition(Duration.millis(210), modalNode);
        scale.setToX(drawerStyle ? 0.985 : 0.94);
        scale.setToY(drawerStyle ? 0.985 : 0.94);

        RotateTransition rotate = new RotateTransition(Duration.millis(180), modalNode);
        rotate.setToAngle(drawerStyle ? -1.2 : 1.4);

        Object blurredNodesObj = overlay.getProperties().get("px.overlay.blurredNodes");
        Object oldEffectsObj = overlay.getProperties().get("px.overlay.oldEffects");
        Object oldOpacitiesObj = overlay.getProperties().get("px.overlay.oldOpacities");
        Timeline blurOut = new Timeline();
        if (blurredNodesObj instanceof List<?> && oldEffectsObj instanceof Map<?, ?> && oldOpacitiesObj instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            List<Node> blurredNodes = (List<Node>) blurredNodesObj;
            @SuppressWarnings("unchecked")
            Map<Node, Effect> oldEffects = (Map<Node, Effect>) oldEffectsObj;
            @SuppressWarnings("unchecked")
            Map<Node, Double> oldOpacities = (Map<Node, Double>) oldOpacitiesObj;
            for (Node node : blurredNodes) {
                if (node.getEffect() instanceof GaussianBlur gb) {
                    blurOut.getKeyFrames().addAll(
                            new KeyFrame(Duration.ZERO, new KeyValue(gb.radiusProperty(), gb.getRadius(), Interpolator.EASE_BOTH), new KeyValue(node.opacityProperty(), node.getOpacity(), Interpolator.EASE_BOTH)),
                            new KeyFrame(Duration.millis(190), new KeyValue(gb.radiusProperty(), 0, Interpolator.EASE_BOTH), new KeyValue(node.opacityProperty(), oldOpacities.getOrDefault(node, 1.0), Interpolator.EASE_BOTH))
                    );
                }
            }
        }

        ParallelTransition out = new ParallelTransition(bg, fade, slide, scale, rotate, blurOut);
        out.setOnFinished(e -> {
            if (blurredNodesObj instanceof List<?> && oldEffectsObj instanceof Map<?, ?> && oldOpacitiesObj instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                List<Node> blurredNodes = (List<Node>) blurredNodesObj;
                @SuppressWarnings("unchecked")
                Map<Node, Effect> oldEffects = (Map<Node, Effect>) oldEffectsObj;
                @SuppressWarnings("unchecked")
                Map<Node, Double> oldOpacities = (Map<Node, Double>) oldOpacitiesObj;
                for (Node node : blurredNodes) {
                    node.setOpacity(oldOpacities.getOrDefault(node, 1.0));
                    node.setEffect(oldEffects.get(node));
                }
            }

            Parent parent = overlay.getParent();
            if (parent instanceof Pane) {
                ((Pane) parent).getChildren().remove(overlay);
            }
            Object cb = overlay.getProperties().get("px.overlay.onClosed");
            if (cb instanceof Runnable) {
                ((Runnable) cb).run();
            }
        });
        out.playFromStart();
    }

    public static void applyRoundedClip(Region node, double radius) {
    Rectangle clip = new Rectangle();
    clip.widthProperty().bind(node.widthProperty());
    clip.heightProperty().bind(node.heightProperty());
    clip.setArcWidth(radius * 2);
    clip.setArcHeight(radius * 2);
    node.setClip(clip);
}
    public static void installSearchFieldFX(Node glowNode, TextField field, double expandedWidth, double normalWidth) {
    if (field == null) return;

    field.getStyleClass().add("search-field-cyber");

    javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow();
    glow.setRadius(12);
    glow.setSpread(0.25);
    glow.setColor(javafx.scene.paint.Color.web("#00eaff"));

    field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
        javafx.animation.Timeline t;

        if (isFocused) {
            field.setEffect(glow);

            t = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(180),
                    new javafx.animation.KeyValue(field.scaleXProperty(), 1.03, javafx.animation.Interpolator.EASE_BOTH),
                    new javafx.animation.KeyValue(field.scaleYProperty(), 1.03, javafx.animation.Interpolator.EASE_BOTH),
                    new javafx.animation.KeyValue(field.opacityProperty(), 1.0, javafx.animation.Interpolator.EASE_BOTH)
                )
            );

            if (glowNode instanceof javafx.scene.layout.Region region) {
                t.getKeyFrames().add(
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(180),
                        new javafx.animation.KeyValue(region.prefWidthProperty(), expandedWidth, javafx.animation.Interpolator.EASE_BOTH)
                    )
                );
            }

            t.play();

        } else {
            field.setEffect(null);

            t = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(160),
                    new javafx.animation.KeyValue(field.scaleXProperty(), 1.0, javafx.animation.Interpolator.EASE_BOTH),
                    new javafx.animation.KeyValue(field.scaleYProperty(), 1.0, javafx.animation.Interpolator.EASE_BOTH)
                )
            );

            if (glowNode instanceof javafx.scene.layout.Region region) {
                t.getKeyFrames().add(
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(160),
                        new javafx.animation.KeyValue(region.prefWidthProperty(), normalWidth, javafx.animation.Interpolator.EASE_BOTH)
                    )
                );
            }

            t.play();
        }
    });

    field.setOnMouseEntered(e -> {
        if (!field.isFocused()) {
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(120), field);
            st.setToX(1.015);
            st.setToY(1.015);
            st.play();
        }
    });

    field.setOnMouseExited(e -> {
        if (!field.isFocused()) {
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(120), field);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        }
    });

    field.textProperty().addListener((obs, oldText, newText) -> {
        if (newText != null && !newText.isEmpty()) {
            javafx.scene.effect.DropShadow typingGlow = new javafx.scene.effect.DropShadow();
            typingGlow.setColor(javafx.scene.paint.Color.web("#7af7ff"));
            typingGlow.setRadius(16);
            field.setEffect(typingGlow);

            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(220));
            pause.setOnFinished(ev -> {
                if (field.isFocused()) {
                    field.setEffect(glow);
                } else {
                    field.setEffect(null);
                }
            });
            pause.play();
        }
    });

    field.setOnAction(e -> {
        javafx.animation.Timeline glitch = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                new javafx.animation.KeyValue(field.opacityProperty(), 0.65)
            ),
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(55),
                new javafx.animation.KeyValue(field.opacityProperty(), 1.0)
            ),
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(110),
                new javafx.animation.KeyValue(field.opacityProperty(), 0.78)
            ),
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(165),
                new javafx.animation.KeyValue(field.opacityProperty(), 1.0)
            )
        );
        glitch.play();
    });
}
    public static void installImageHover(javafx.scene.Node node, double hoverScale) {
    if (node == null) return;

    final javafx.scene.effect.Effect baseEffect = node.getEffect();

    node.setOnMouseEntered(e -> {
        javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(180), node);
        scale.setToX(hoverScale);
        scale.setToY(hoverScale);
        scale.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        scale.play();

        javafx.animation.TranslateTransition lift = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(180), node);
        lift.setToY(-4);
        lift.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        lift.play();

        javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow();
        glow.setRadius(18);
        glow.setSpread(0.25);
        glow.setColor(javafx.scene.paint.Color.web("#6ffcff"));
        node.setEffect(glow);
    });

    node.setOnMouseExited(e -> {
        javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(180), node);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        scale.play();

        javafx.animation.TranslateTransition lift = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(180), node);
        lift.setToY(0);
        lift.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        lift.play();

        node.setEffect(baseEffect);
    });
}   
    public static void playEntrance(javafx.scene.Node node, double fromY, int durationMs, int delayMs) {
    if (node == null) return;

    node.setOpacity(0.0);
    node.setTranslateY(fromY);

    javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(durationMs), node);
    fade.setFromValue(0.0);
    fade.setToValue(1.0);
    fade.setDelay(javafx.util.Duration.millis(delayMs));
    fade.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

    javafx.animation.TranslateTransition move = new javafx.animation.TranslateTransition(
            javafx.util.Duration.millis(durationMs), node);
    move.setFromY(fromY);
    move.setToY(0.0);
    move.setDelay(javafx.util.Duration.millis(delayMs));
    move.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

    javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(
            javafx.util.Duration.millis(durationMs), node);
    scale.setFromX(0.96);
    scale.setFromY(0.96);
    scale.setToX(1.0);
    scale.setToY(1.0);
    scale.setDelay(javafx.util.Duration.millis(delayMs));
    scale.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

    new javafx.animation.ParallelTransition(fade, move, scale).play();
}

    // =========================================================================
    // NOTIFICATION PANEL — slide-in from right, positioned TOP-RIGHT
    // =========================================================================

    /**
     * Shows the notification popup as a right-side panel that slides in from
     * the screen edge and anchors to the top-right corner of the host stack.
     */
    public static void showNotificationFromRight(
            StackPane hostStack, Parent modalRoot, Runnable onClose) {

        if (hostStack == null || modalRoot == null) return;

        StackPane overlay = new StackPane();
        overlay.setPickOnBounds(true);
        overlay.setOpacity(0.0);
        overlay.setManaged(true);
        overlay.setVisible(true);
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        overlay.setStyle(
            "-fx-background-color: linear-gradient(" +
            "to right, rgba(0,0,0,0.15) 0%, rgba(4,8,22,0.48) 100%);");

        // Pin popup to TOP-RIGHT of the host and prevent full-screen stretch
        StackPane.setAlignment(modalRoot, Pos.TOP_RIGHT);
        StackPane.setMargin(modalRoot, new Insets(48, 10, 0, 0));
        // Constrain the root so it only takes its natural (card) size
        if (modalRoot instanceof Region mr) {
            mr.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            mr.setMinHeight(Region.USE_COMPUTED_SIZE);
            mr.setPrefHeight(Region.USE_COMPUTED_SIZE);
        }

        Node card = resolveNotificationCard(modalRoot);

        overlay.getProperties().put("px.overlay.content",    Boolean.TRUE);
        overlay.getProperties().put("px.overlay.root",       modalRoot);
        overlay.getProperties().put("px.overlay.card",       card);
        overlay.getProperties().put("px.overlay.drawerStyle", Boolean.FALSE);
        overlay.getProperties().put("px.overlay.notifStyle",  Boolean.TRUE);
        modalRoot.getProperties().put("px.overlay.host", overlay);
        card.getProperties().put("px.overlay.host", overlay);
        if (onClose != null) {
            overlay.getProperties().put("px.overlay.onClosed", onClose);
        }

        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) closeNotificationOverlay(overlay);
        });

        overlay.getChildren().add(modalRoot);
        hostStack.getChildren().add(overlay);
        overlay.toFront();
        applyTo(modalRoot);

        // Off-screen initial state
        card.setOpacity(0.0);
        card.setTranslateX(440.0);
        card.setTranslateY(-14.0);
        card.setScaleX(0.92);
        card.setScaleY(0.92);
        card.setRotate(2.8);

        FadeTransition bgFade = new FadeTransition(Duration.millis(240), overlay);
        bgFade.setFromValue(0.0);
        bgFade.setToValue(1.0);
        bgFade.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition cardFade = new FadeTransition(Duration.millis(360), card);
        cardFade.setFromValue(0.0);
        cardFade.setToValue(1.0);
        cardFade.setInterpolator(Interpolator.EASE_OUT);

        // Spring slide: overshoot left then settle
        TranslateTransition slideX1 = new TranslateTransition(Duration.millis(460), card);
        slideX1.setFromX(440.0);
        slideX1.setToX(-8.0);
        slideX1.setInterpolator(Interpolator.SPLINE(0.06, 0.94, 0.22, 1.0));

        TranslateTransition slideX2 = new TranslateTransition(Duration.millis(150), card);
        slideX2.setFromX(-8.0);
        slideX2.setToX(3.0);
        slideX2.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition slideX3 = new TranslateTransition(Duration.millis(100), card);
        slideX3.setFromX(3.0);
        slideX3.setToX(0.0);
        slideX3.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition slideY = new TranslateTransition(Duration.millis(420), card);
        slideY.setFromY(-14.0);
        slideY.setToY(0.0);
        slideY.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(440), card);
        scaleIn.setFromX(0.92);
        scaleIn.setFromY(0.92);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);
        scaleIn.setInterpolator(Interpolator.SPLINE(0.12, 0.96, 0.24, 1.0));

        RotateTransition rotIn = new RotateTransition(Duration.millis(400), card);
        rotIn.setFromAngle(2.8);
        rotIn.setToAngle(0.0);
        rotIn.setInterpolator(Interpolator.EASE_OUT);

        SequentialTransition xSpring = new SequentialTransition(slideX1, slideX2, slideX3);
        ParallelTransition cardAnim = new ParallelTransition(cardFade, xSpring, slideY, scaleIn, rotIn);

        ParallelTransition intro = new ParallelTransition(bgFade, cardAnim);
        intro.setOnFinished(e -> {
            card.setTranslateX(0); card.setTranslateY(0);
            card.setScaleX(1.0);  card.setScaleY(1.0);
            card.setRotate(0.0);
        });
        intro.playFromStart();
    }

    /** Resolve the inner animated card from the notification popup StackPane. */
    private static Node resolveNotificationCard(Parent modalRoot) {
        if (modalRoot instanceof StackPane sp) {
            for (Node child : sp.getChildren()) {
                if (child == null) continue;
                if (child.getStyleClass().contains("notify-popup-card")) return child;
                if (child.isManaged() && child.isVisible()
                        && (child instanceof javafx.scene.layout.VBox
                            || (child instanceof Region
                                && !child.getStyleClass().contains("notify-edge-glow")
                                && !child.getStyleClass().contains("notify-scanline")))) {
                    return child;
                }
            }
        }
        return modalRoot;
    }

    /** Close a notification overlay with a right-slide-out exit animation. */
    private static void closeNotificationOverlay(StackPane overlay) {
        if (overlay == null) return;
        Node card = (Node) overlay.getProperties().get("px.overlay.card");
        if (card == null) { closeOverlayHost(overlay); return; }

        Object aura = card.getProperties().get("px.modal.aura");
        if (aura instanceof Animation a) {
            a.stop();
            card.getProperties().remove("px.modal.aura");
        }

        FadeTransition bgFade = new FadeTransition(Duration.millis(200), overlay);
        bgFade.setToValue(0.0);
        bgFade.setInterpolator(Interpolator.EASE_IN);

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(260), card);
        slideOut.setToX(440.0);
        slideOut.setInterpolator(Interpolator.SPLINE(0.4, 0.0, 0.8, 0.6));

        FadeTransition cardFade = new FadeTransition(Duration.millis(200), card);
        cardFade.setToValue(0.0);
        cardFade.setInterpolator(Interpolator.EASE_IN);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(220), card);
        scaleOut.setToX(0.94);
        scaleOut.setToY(0.94);
        scaleOut.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition out = new ParallelTransition(bgFade, slideOut, cardFade, scaleOut);
        out.setOnFinished(e -> {
            Parent parent = overlay.getParent();
            if (parent instanceof Pane p) p.getChildren().remove(overlay);
            Object cb = overlay.getProperties().get("px.overlay.onClosed");
            if (cb instanceof Runnable r) r.run();
        });
        out.playFromStart();
    }

    // =========================================================================
    // PAGE SWITCHING — reliable horizontal slide transition
    // =========================================================================

    /**
     * Switches pages inside the dashboard content StackPane with a cinematic
     * horizontal slide.
     *
     * Exit  (old page): fades out + slides 60 px to the LEFT over 200 ms.
     * Enter (new page): simultaneously fades in + slides from +60 px to 0
     *                   over 300 ms using a spring cubic bezier.
     *
     * Uses ONLY opacity and translateX — no GaussianBlur Timeline — so it
     * is 100 % reliable on freshly-loaded FXML nodes.
     *
     * @param contentStack  Dashboard StackPane (content area).
     * @param newPage       Already-loaded page root to transition to.
     */
    public static void switchSceneAnimated(StackPane contentStack, Parent newPage) {
        if (contentStack == null || newPage == null) return;

        // Find the currently visible page
        Node current = null;
        for (Node n : contentStack.getChildren()) {
            if (n != newPage && n.isVisible() && n.isManaged()) { current = n; break; }
        }

        // Add new page to the host if it is not already there
        if (!contentStack.getChildren().contains(newPage)) {
            contentStack.getChildren().add(newPage);
        }

        // ── CLEAR entrance flags + PRE-HIDE cards ───────────────────────────
        // Clears the px.motion.entrance guard so playEntrance() re-animates
        // cards on every page switch, and hides them immediately so there is
        // no "all-cards-visible flash before pop-in" artefact.
        clearEntranceFlags(newPage);

        // Prepare the incoming page: transparent, shifted right, in front
        newPage.setManaged(true);
        newPage.setVisible(true);
        newPage.setOpacity(0.0);
        newPage.setTranslateX(60.0);
        newPage.setTranslateY(0.0);
        newPage.setScaleX(1.0);
        newPage.setScaleY(1.0);
        newPage.toFront();

        final Node finalCurrent = current;

        // ── ENTER animation (always plays) ──────────────────────────────────
        FadeTransition enterFade = new FadeTransition(Duration.millis(300), newPage);
        enterFade.setFromValue(0.0);
        enterFade.setToValue(1.0);
        enterFade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition enterSlide = new TranslateTransition(Duration.millis(340), newPage);
        enterSlide.setFromX(60.0);
        enterSlide.setToX(0.0);
        enterSlide.setInterpolator(Interpolator.SPLINE(0.08, 0.94, 0.20, 1.0));

        ParallelTransition enterAnim = new ParallelTransition(enterFade, enterSlide);
        enterAnim.setOnFinished(e -> {
            newPage.setOpacity(1.0);
            newPage.setTranslateX(0.0);
            // Fire hover/float effects AND entrance animations for static nodes
            // AFTER the page is fully visible. Dynamic cards that arrived during
            // the transition were already marked by their spawn animation and will
            // be skipped by playEntrance. Cards arriving later use spawn animations.
            Platform.runLater(() -> applyTo(newPage));
        });

        if (current != null) {
            // ── EXIT animation ───────────────────────────────────────────────
            FadeTransition exitFade = new FadeTransition(Duration.millis(190), current);
            exitFade.setFromValue(1.0);
            exitFade.setToValue(0.0);
            exitFade.setInterpolator(Interpolator.EASE_IN);

            TranslateTransition exitSlide = new TranslateTransition(Duration.millis(200), current);
            exitSlide.setFromX(0.0);
            exitSlide.setToX(-60.0);
            exitSlide.setInterpolator(Interpolator.EASE_IN);

            ParallelTransition exitAnim = new ParallelTransition(exitFade, exitSlide);
            exitAnim.setOnFinished(e -> {
                finalCurrent.setVisible(false);
                finalCurrent.setManaged(false);
                finalCurrent.setOpacity(1.0);
                finalCurrent.setTranslateX(0.0);
            });

            // Both play together — exit and enter overlap for a smooth swap
            new ParallelTransition(exitAnim, enterAnim).playFromStart();

        } else {
            enterAnim.playFromStart();
        }
    }
    public static void styleDateCell(DateCell cell, boolean isCurrentMonth) {

    // Reset
    cell.setOpacity(1);
    cell.setScaleX(1);
    cell.setScaleY(1);
    cell.setDisable(false);

    if (!isCurrentMonth) {
        // Soft fade + slight shrink for other-month days
        fadeNode(cell, 0.35, 150);
        scaleNode(cell, 0.9, 150);
        cell.setDisable(true);
    } else {
        fadeNode(cell, 1.0, 150);
        scaleNode(cell, 1.0, 150);

        // Add hover animation (same vibe as your cards)
        addHoverEffect(cell);
    }
}
    public static void fadeNode(Node node, double toValue, int duration) {
    FadeTransition ft = new FadeTransition(Duration.millis(duration), node);
    ft.setToValue(toValue);
    ft.play();
}

public static void scaleNode(Node node, double toScale, int duration) {
    ScaleTransition st = new ScaleTransition(Duration.millis(duration), node);
    st.setToX(toScale);
    st.setToY(toScale);
    st.play();
}

public static void addHoverEffect(Node node) {
    node.setOnMouseEntered(e -> scaleNode(node, 1.08, 120));
    node.setOnMouseExited(e -> scaleNode(node, 1.0, 120));
}
public static void animateCalendarPopup(Parent root) {
    root.setOpacity(0);
    root.setScaleY(0.9);

    FadeTransition ft = new FadeTransition(Duration.millis(180), root);
    ft.setToValue(1);

    ScaleTransition st = new ScaleTransition(Duration.millis(180), root);
    st.setToY(1);

    new ParallelTransition(ft, st).play();
}
}
