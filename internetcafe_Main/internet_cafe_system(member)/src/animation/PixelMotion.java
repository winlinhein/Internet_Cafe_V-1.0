package animation;

import java.util.Random;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.Chart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

public final class PixelMotion {
    private static final Random RNG = new Random();

    private PixelMotion() {
    }

    public static void playEntrance(Node node, double fromY, long durationMs, long delayMs) {
        if (node == null) {
            return;
        }

        node.setOpacity(0);
        node.setTranslateY(fromY);
        node.setScaleX(0.985);
        node.setScaleY(0.985);

        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setDelay(Duration.millis(delayMs));

        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromY(fromY);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        slide.setDelay(Duration.millis(delayMs));

        ScaleTransition scale = new ScaleTransition(Duration.millis(durationMs), node);
        scale.setFromX(0.985);
        scale.setFromY(0.985);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);
        scale.setDelay(Duration.millis(delayMs));

        new ParallelTransition(fade, slide, scale).play();
    }

    public static void installCardHover(Node card, Node mediaNode, Node accentNode) {
        installCardHover(card, mediaNode, accentNode, 1.03, -6, 1.06);
    }

    public static void installCardHover(Node card, Node mediaNode, Node accentNode,
                                        double hoverScale, double hoverLiftY, double mediaScale) {
        if (card == null) {
            return;
        }

        card.setOnMouseEntered(e -> playCardHover(card, mediaNode, accentNode, true, hoverScale, hoverLiftY, mediaScale));
        card.setOnMouseExited(e -> playCardHover(card, mediaNode, accentNode, false, hoverScale, hoverLiftY, mediaScale));
    }

    private static void playCardHover(Node card, Node mediaNode, Node accentNode,
                                      boolean hover, double hoverScale, double hoverLiftY, double mediaScale) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(180), card);
        scale.setToX(hover ? hoverScale : 1.0);
        scale.setToY(hover ? hoverScale : 1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition lift = new TranslateTransition(Duration.millis(180), card);
        lift.setToY(hover ? hoverLiftY : 0);
        lift.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition group;
        if (mediaNode != null && accentNode != null) {
            ScaleTransition imageScale = new ScaleTransition(Duration.millis(220), mediaNode);
            imageScale.setToX(hover ? mediaScale : 1.0);
            imageScale.setToY(hover ? mediaScale : 1.0);
            imageScale.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition accentFade = new FadeTransition(Duration.millis(180), accentNode);
            accentFade.setToValue(hover ? 1.0 : 0.85);

            group = new ParallelTransition(scale, lift, imageScale, accentFade);
        } else if (mediaNode != null) {
            ScaleTransition imageScale = new ScaleTransition(Duration.millis(220), mediaNode);
            imageScale.setToX(hover ? mediaScale : 1.0);
            imageScale.setToY(hover ? mediaScale : 1.0);
            imageScale.setInterpolator(Interpolator.EASE_OUT);

            group = new ParallelTransition(scale, lift, imageScale);
        } else {
            group = new ParallelTransition(scale, lift);
        }

        group.play();
    }

    public static void installButtonSlideHover(Button button, double hoverX) {
        if (button == null) {
            return;
        }

        button.setOnMouseEntered(e -> {
            TranslateTransition slide = new TranslateTransition(Duration.millis(170), button);
            slide.setToX(hoverX);
            slide.setInterpolator(Interpolator.EASE_OUT);
            slide.play();
        });

        button.setOnMouseExited(e -> {
            TranslateTransition slide = new TranslateTransition(Duration.millis(170), button);
            slide.setToX(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            slide.play();
        });
    }

    public static void pulse(Node node, double toScale, long durationMs) {
        if (node == null) {
            return;
        }

        ScaleTransition pulse = new ScaleTransition(Duration.millis(durationMs), node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(toScale);
        pulse.setToY(toScale);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        pulse.play();
    }


    public static void installSearchFieldFX(StackPane shell, TextField field, double focusWidth, double idleWidth) {
        if (field == null) {
            return;
        }

        field.setOnMouseEntered(e -> pulse(shell != null ? shell : field, 1.01, 180));

        field.focusedProperty().addListener((obs, oldV, newV) -> {
            Region target = shell != null ? shell : field;
            if (shell != null) {
                shell.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("active"), newV);
            }

            if (newV) {
                createWidthMorph(target, focusWidth, 220).play();
                pulse(target, 1.012, 220);
            } else {
                createWidthMorph(target, idleWidth, 180).play();
            }
        });
    }

    public static Timeline createWidthMorph(Region region, double targetWidth, long durationMs) {
        return new Timeline(
            new KeyFrame(Duration.millis(durationMs),
                new KeyValue(region.prefWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                new KeyValue(region.minWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                new KeyValue(region.maxWidthProperty(), targetWidth, Interpolator.EASE_BOTH)
            )
        );
    }

    public static void animatePageSwapOut(Node node, Runnable onFinished) {
        if (node == null) {
            if (onFinished != null) {
                onFinished.run();
            }
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(120), node);
        fadeOut.setToValue(0);

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(120), node);
        slideOut.setToY(-10);
        slideOut.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition out = new ParallelTransition(fadeOut, slideOut);
        out.setOnFinished(e -> {
            node.setOpacity(1);
            node.setTranslateY(0);
            if (onFinished != null) {
                onFinished.run();
            }
        });
        out.play();
    }

    public static void animatePageSwapIn(Node node) {
        playEntrance(node, 8, 180, 0);
    }

    public static void installImageHover(ImageView imageView, double hoverScale) {
        if (imageView == null) {
            return;
        }

        imageView.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(180), imageView);
            scale.setToX(hoverScale);
            scale.setToY(hoverScale);
            scale.setInterpolator(Interpolator.EASE_OUT);
            scale.play();
        });

        imageView.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(180), imageView);
            scale.setToX(1);
            scale.setToY(1);
            scale.setInterpolator(Interpolator.EASE_OUT);
            scale.play();
        });
    }

    public enum ToastType { OK, WARN, ERROR, INFO }

    public static void toastGlitch(Node anyNodeInScene, String title, String message, ToastType type) {
        if (anyNodeInScene == null) {
            return;
        }

        Runnable show = () -> {
            if (anyNodeInScene.getScene() == null || anyNodeInScene.getScene().getWindow() == null) {
                return;
            }
            showToast(anyNodeInScene.getScene().getWindow(), title, message, type);
        };

        if (Platform.isFxApplicationThread()) {
            show.run();
        } else {
            Platform.runLater(show);
        }
    }

    private static void showToast(Window window, String title, String message, ToastType type) {
        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(false);
        popup.setHideOnEscape(true);

        StackPane root = new StackPane();
        root.setPadding(new Insets(0));

        VBox card = new VBox(10);
        card.setPadding(new Insets(14, 16, 12, 16));
        card.setMinWidth(320);
        card.setMaxWidth(380);
        card.setTranslateY(18);
        card.setOpacity(0);

        String border = borderColor(type);
        String background = backgroundColor(type);
        String accent = accentColor(type);

        card.setStyle(
            "-fx-background-color: " + background + ";" +
            "-fx-background-radius: 16;" +
            "-fx-border-color: " + border + ";" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 16;"
        );

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web(accent, 0.34));
        shadow.setRadius(type == ToastType.ERROR ? 26 : 22);
        shadow.setSpread(0.12);
        shadow.setOffsetY(10);
        card.setEffect(shadow);

        Region scanline = new Region();
        scanline.setManaged(false);
        scanline.prefWidthProperty().bind(card.widthProperty());
        scanline.setPrefHeight(14);
        scanline.setMaxHeight(14);
        scanline.setOpacity(0);
        scanline.setMouseTransparent(true);
        scanline.setStyle(
            "-fx-background-color: linear-gradient(to bottom, transparent 0%, rgba(255,255,255,0.28) 45%, transparent 100%);"
        );
        StackPane.setAlignment(scanline, Pos.TOP_CENTER);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label badge = new Label(toastBadge(type));
        badge.setMinSize(28, 28);
        badge.setPrefSize(28, 28);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle(
            "-fx-background-color: " + accent + ";" +
            "-fx-background-radius: 14;" +
            "-fx-text-fill: #04131b;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: bold;"
        );

        StackPane titleStack = new StackPane();
        titleStack.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleStack, Priority.ALWAYS);

        Label titleMain = new Label(title == null ? "" : title);
        titleMain.setStyle("-fx-text-fill: #f5fbff; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label titleGhostA = new Label(titleMain.getText());
        titleGhostA.setMouseTransparent(true);
        titleGhostA.setStyle("-fx-text-fill: rgba(0,255,255,0.36); -fx-font-size: 14px; -fx-font-weight: bold;");

        Label titleGhostB = new Label(titleMain.getText());
        titleGhostB.setMouseTransparent(true);
        titleGhostB.setStyle("-fx-text-fill: rgba(255,65,140,0.34); -fx-font-size: 14px; -fx-font-weight: bold;");

        titleStack.getChildren().addAll(titleGhostA, titleGhostB, titleMain);

        header.getChildren().addAll(badge, titleStack);

        Label subtitle = new Label(toastSubtitle(type));
        subtitle.setStyle("-fx-text-fill: " + accent + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        StackPane messageStack = new StackPane();
        messageStack.setAlignment(Pos.CENTER_LEFT);

        Label messageMain = new Label(message == null ? "" : message);
        messageMain.setWrapText(true);
        messageMain.setStyle("-fx-text-fill: rgba(240,248,255,0.95); -fx-font-size: 11px;");

        Label messageGhostA = new Label(messageMain.getText());
        messageGhostA.setWrapText(true);
        messageGhostA.setMouseTransparent(true);
        messageGhostA.setStyle("-fx-text-fill: rgba(0,255,255,0.18); -fx-font-size: 11px;");

        Label messageGhostB = new Label(messageMain.getText());
        messageGhostB.setWrapText(true);
        messageGhostB.setMouseTransparent(true);
        messageGhostB.setStyle("-fx-text-fill: rgba(255,65,140,0.16); -fx-font-size: 11px;");

        messageStack.getChildren().addAll(messageGhostA, messageGhostB, messageMain);

        Region timerBar = new Region();
        timerBar.setPrefHeight(3);
        timerBar.setMinHeight(3);
        timerBar.setMaxHeight(3);
        timerBar.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 999;");
        timerBar.setScaleX(1);

        card.getChildren().addAll(header, subtitle, messageStack, timerBar);
        root.getChildren().addAll(card, scanline);
        popup.getContent().add(root);

        popup.show(window);

        root.applyCss();
        root.layout();
        double width = Math.max(card.prefWidth(-1), root.prefWidth(-1));
        double height = Math.max(card.prefHeight(-1), root.prefHeight(-1));
        popup.setX(window.getX() + window.getWidth() - width - 24);
        popup.setY(window.getY() + window.getHeight() - height - 28);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), card);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(220), card);
        slideIn.setFromY(18);
        slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fadeIn, slideIn).play();
        playToastJitter(root, scanline, titleGhostA, titleGhostB, messageGhostA, messageGhostB, type);

        double seconds = type == ToastType.ERROR ? 4.2 : 3.2;
        Timeline timer = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(timerBar.scaleXProperty(), 1.0, Interpolator.LINEAR)),
            new KeyFrame(Duration.seconds(seconds), new KeyValue(timerBar.scaleXProperty(), 0.0, Interpolator.LINEAR))
        );

        Runnable dismiss = () -> {
            Timeline micro = (Timeline) root.getProperties().remove("px.toast.micro");
            if (micro != null) {
                micro.stop();
            }
            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), card);
            fadeOut.setToValue(0);
            TranslateTransition slideOut = new TranslateTransition(Duration.millis(150), card);
            slideOut.setToY(-8);
            ParallelTransition out = new ParallelTransition(fadeOut, slideOut);
            out.setOnFinished(e -> popup.hide());
            out.play();
        };

        timer.setOnFinished(e -> dismiss.run());
        timer.playFromStart();

        PauseTransition repositionDelay = new PauseTransition(Duration.millis(30));
        repositionDelay.setOnFinished(e -> {
            popup.setX(window.getX() + window.getWidth() - width - 24);
            popup.setY(window.getY() + window.getHeight() - height - 28);
        });
        repositionDelay.play();
    }

    private static void playToastJitter(Node root, Region scanline, Node titleGhostA, Node titleGhostB,
                                        Node messageGhostA, Node messageGhostB, ToastType type) {
        double amp = type == ToastType.ERROR ? 5.0 : 3.0;

        Timeline intro = new Timeline(
            glitchFrame(0, root, 0, 0, 0),
            glitchFrame(28, root, -amp, 0, -1.0),
            glitchFrame(56, root, amp, 0, 1.0),
            glitchFrame(92, root, -amp * 0.5, 0, -0.5),
            glitchFrame(128, root, 0, 0, 0)
        );
        intro.playFromStart();

        Timeline ghosts = new Timeline(
            ghostFrame(0, titleGhostA, -amp, 0, 0.38),
            ghostFrame(0, titleGhostB, amp, 0, 0.32),
            ghostFrame(0, messageGhostA, -amp * 0.7, 0, 0.22),
            ghostFrame(0, messageGhostB, amp * 0.7, 0, 0.18),
            ghostFrame(110, titleGhostA, 0, 0, 0),
            ghostFrame(110, titleGhostB, 0, 0, 0),
            ghostFrame(110, messageGhostA, 0, 0, 0),
            ghostFrame(110, messageGhostB, 0, 0, 0)
        );
        ghosts.playFromStart();

        FadeTransition scanFlash = new FadeTransition(Duration.millis(110), scanline);
        scanFlash.setFromValue(0.0);
        scanFlash.setToValue(0.34);
        scanFlash.setAutoReverse(true);
        scanFlash.setCycleCount(2);
        scanFlash.playFromStart();

        Timeline micro = new Timeline(new KeyFrame(Duration.millis(type == ToastType.ERROR ? 1400 : 1900), e -> {
            if (RNG.nextDouble() > 0.4) {
                return;
            }

            FadeTransition pulse = new FadeTransition(Duration.millis(60), scanline);
            pulse.setFromValue(0.0);
            pulse.setToValue(0.22);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(2);
            pulse.playFromStart();

            Timeline nudge = new Timeline(
                glitchFrame(0, root, 0, 0, 0),
                glitchFrame(22, root, RNG.nextBoolean() ? 1.8 : -1.8, RNG.nextBoolean() ? 1.0 : -1.0, 0.4),
                glitchFrame(44, root, 0, 0, 0)
            );
            nudge.playFromStart();
        }));
        micro.setCycleCount(Animation.INDEFINITE);
        micro.play();
        root.getProperties().put("px.toast.micro", micro);
    }

    private static KeyFrame glitchFrame(int ms, Node node, double x, double y, double rotate) {
        return new KeyFrame(Duration.millis(ms),
            new KeyValue(node.translateXProperty(), x, Interpolator.DISCRETE),
            new KeyValue(node.translateYProperty(), y, Interpolator.DISCRETE),
            new KeyValue(node.rotateProperty(), rotate, Interpolator.DISCRETE)
        );
    }

    private static KeyFrame ghostFrame(int ms, Node node, double x, double y, double opacity) {
        return new KeyFrame(Duration.millis(ms),
            new KeyValue(node.translateXProperty(), x, Interpolator.DISCRETE),
            new KeyValue(node.translateYProperty(), y, Interpolator.DISCRETE),
            new KeyValue(node.opacityProperty(), opacity, Interpolator.DISCRETE)
        );
    }

    private static String borderColor(ToastType type) {
        return switch (type == null ? ToastType.INFO : type) {
            case OK -> "#6ef2b5";
            case WARN -> "#ffd166";
            case ERROR -> "#ff6b8d";
            case INFO -> "#5fd8ff";
        };
    }

    private static String accentColor(ToastType type) {
        return switch (type == null ? ToastType.INFO : type) {
            case OK -> "#8fffd0";
            case WARN -> "#ffe08a";
            case ERROR -> "#ff8ea7";
            case INFO -> "#7ce8ff";
        };
    }

    private static String backgroundColor(ToastType type) {
        return switch (type == null ? ToastType.INFO : type) {
            case OK -> "rgba(10, 28, 24, 0.96)";
            case WARN -> "rgba(30, 24, 8, 0.96)";
            case ERROR -> "rgba(34, 10, 18, 0.96)";
            case INFO -> "rgba(9, 18, 30, 0.96)";
        };
    }

    private static String toastBadge(ToastType type) {
        return switch (type == null ? ToastType.INFO : type) {
            case OK -> "✓";
            case WARN -> "!";
            case ERROR -> "×";
            case INFO -> "i";
        };
    }

    private static String toastSubtitle(ToastType type) {
        return switch (type == null ? ToastType.INFO : type) {
            case OK -> "Success";
            case WARN -> "Warning";
            case ERROR -> "Error";
            case INFO -> "Info";
        };
    }

    public static void animateChartEntrance(Node chart, boolean pie) {
        if (chart == null) {
            return;
        }

        if (chart instanceof Chart chartNode) {
            chartNode.applyCss();
            chartNode.layout();
        }

        chart.setOpacity(0.0);
        chart.setTranslateY(pie ? 26.0 : 20.0);
        chart.setScaleX(pie ? 0.84 : 0.94);
        chart.setScaleY(pie ? 0.84 : 0.94);
        chart.setRotate(pie ? -14.0 : 0.0);

        Timeline intro = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(chart.opacityProperty(), 0.0),
                new KeyValue(chart.translateYProperty(), pie ? 26.0 : 20.0),
                new KeyValue(chart.scaleXProperty(), pie ? 0.84 : 0.94),
                new KeyValue(chart.scaleYProperty(), pie ? 0.84 : 0.94),
                new KeyValue(chart.rotateProperty(), pie ? -14.0 : 0.0)
            ),
            new KeyFrame(Duration.millis(pie ? 500 : 360),
                new KeyValue(chart.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(chart.translateYProperty(), 0.0, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0)),
                new KeyValue(chart.scaleXProperty(), pie ? 1.02 : 1.0, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0)),
                new KeyValue(chart.scaleYProperty(), pie ? 1.02 : 1.0, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0)),
                new KeyValue(chart.rotateProperty(), pie ? 6.0 : 0.0, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0))
            ),
            new KeyFrame(Duration.millis(pie ? 720 : 520),
                new KeyValue(chart.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(chart.scaleYProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(chart.rotateProperty(), 0.0, Interpolator.EASE_BOTH)
            )
        );
        intro.playFromStart();

        if (chart instanceof Chart chartNode) {
            Platform.runLater(() -> {
                chartNode.applyCss();
                chartNode.layout();
                animateChartNodes(chartNode);
            });
        }
    }

    private static void animateChartNodes(Chart chart) {
        if (chart instanceof PieChart pieChart) {
            int index = 0;
            for (PieChart.Data data : pieChart.getData()) {
                Node slice = data.getNode();
                if (slice == null) {
                    continue;
                }

                slice.setOpacity(0.0);
                slice.setScaleX(0.8);
                slice.setScaleY(0.8);
                slice.setRotate(-20.0);

                double delay = Math.min(index, 5) * 80.0;
                Timeline sliceIntro = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(slice.opacityProperty(), 0.0),
                        new KeyValue(slice.scaleXProperty(), 0.8),
                        new KeyValue(slice.scaleYProperty(), 0.8),
                        new KeyValue(slice.rotateProperty(), -20.0)
                    ),
                    new KeyFrame(Duration.millis(300 + delay),
                        new KeyValue(slice.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(slice.scaleXProperty(), 1.05, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0)),
                        new KeyValue(slice.scaleYProperty(), 1.05, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0)),
                        new KeyValue(slice.rotateProperty(), 7.0, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0))
                    ),
                    new KeyFrame(Duration.millis(460 + delay),
                        new KeyValue(slice.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(slice.scaleYProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(slice.rotateProperty(), 0.0, Interpolator.EASE_BOTH)
                    )
                );
                sliceIntro.playFromStart();
                index++;
            }
            return;
        }

        if (chart instanceof XYChart<?, ?> xyChart) {
            boolean isBarChart = chart instanceof BarChart<?, ?>;
            int seriesIndex = 0;
            for (Object seriesObj : xyChart.getData()) {
                if (!(seriesObj instanceof XYChart.Series<?, ?> series)) {
                    continue;
                }

                Node seriesNode = series.getNode();
                if (seriesNode != null) {
                    seriesNode.setOpacity(0.0);
                    seriesNode.setTranslateY(18.0);

                    double delay = Math.min(seriesIndex, 4) * 65.0;
                    Timeline lineIntro = new Timeline(
                        new KeyFrame(Duration.ZERO,
                            new KeyValue(seriesNode.opacityProperty(), 0.0),
                            new KeyValue(seriesNode.translateYProperty(), 18.0)
                        ),
                        new KeyFrame(Duration.millis(280 + delay),
                            new KeyValue(seriesNode.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                            new KeyValue(seriesNode.translateYProperty(), 0.0, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0))
                        )
                    );
                    lineIntro.playFromStart();
                }

                int pointIndex = 0;
                for (Object dataObj : series.getData()) {
                    if (!(dataObj instanceof XYChart.Data<?, ?> data)) {
                        continue;
                    }
                    Node pointNode = data.getNode();
                    if (pointNode == null) {
                        continue;
                    }

                    if (isBarChart) {
                        animateBarNode(pointNode, pointIndex);
                    } else {
                        animatePointNode(pointNode, pointIndex);
                    }
                    pointIndex++;
                }
                seriesIndex++;
            }
        }
    }

    private static void animatePointNode(Node pointNode, int pointIndex) {
        pointNode.setOpacity(0.0);
        pointNode.setTranslateY(24.0);
        pointNode.setScaleX(0.84);
        pointNode.setScaleY(0.84);

        double delay = Math.min(pointIndex, 10) * 35.0;
        Timeline pointIntro = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(pointNode.opacityProperty(), 0.0),
                new KeyValue(pointNode.translateYProperty(), 24.0),
                new KeyValue(pointNode.scaleXProperty(), 0.84),
                new KeyValue(pointNode.scaleYProperty(), 0.84)
            ),
            new KeyFrame(Duration.millis(220 + delay),
                new KeyValue(pointNode.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(pointNode.translateYProperty(), -2.0, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0)),
                new KeyValue(pointNode.scaleXProperty(), 1.03, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0)),
                new KeyValue(pointNode.scaleYProperty(), 1.03, Interpolator.SPLINE(0.18, 0.9, 0.24, 1.0))
            ),
            new KeyFrame(Duration.millis(340 + delay),
                new KeyValue(pointNode.translateYProperty(), 0.0, Interpolator.EASE_BOTH),
                new KeyValue(pointNode.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(pointNode.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)
            )
        );
        pointIntro.playFromStart();
    }

    private static void animateBarNode(Node barNode, int pointIndex) {
        barNode.setOpacity(0.0);
        barNode.setTranslateY(34.0);
        barNode.setScaleX(0.72);
        barNode.setScaleY(0.18);
        barNode.setRotate(-1.6);

        double delay = Math.min(pointIndex, 11) * 42.0;
        Timeline barIntro = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(barNode.opacityProperty(), 0.0),
                new KeyValue(barNode.translateYProperty(), 34.0),
                new KeyValue(barNode.scaleXProperty(), 0.72),
                new KeyValue(barNode.scaleYProperty(), 0.18),
                new KeyValue(barNode.rotateProperty(), -1.6)
            ),
            new KeyFrame(Duration.millis(220 + delay),
                new KeyValue(barNode.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(barNode.translateYProperty(), -8.0, Interpolator.SPLINE(0.16, 0.92, 0.22, 1.0)),
                new KeyValue(barNode.scaleXProperty(), 1.08, Interpolator.SPLINE(0.16, 0.92, 0.22, 1.0)),
                new KeyValue(barNode.scaleYProperty(), 1.12, Interpolator.SPLINE(0.16, 0.92, 0.22, 1.0)),
                new KeyValue(barNode.rotateProperty(), 0.55, Interpolator.SPLINE(0.16, 0.92, 0.22, 1.0))
            ),
            new KeyFrame(Duration.millis(360 + delay),
                new KeyValue(barNode.translateYProperty(), 0.0, Interpolator.EASE_BOTH),
                new KeyValue(barNode.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(barNode.scaleYProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(barNode.rotateProperty(), 0.0, Interpolator.EASE_BOTH)
            )
        );
        barIntro.playFromStart();

        PauseTransition afterGlow = new PauseTransition(Duration.millis(390 + delay));
        afterGlow.setOnFinished(event -> pulse(barNode, 1.025, 150));
        afterGlow.playFromStart();
    }
}
