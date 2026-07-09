package animation;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public final class CyberConfirmBox {

    private CyberConfirmBox() {
    }

    public static boolean show(Node anyNodeInScene, String title, String message) {
        if (anyNodeInScene == null || anyNodeInScene.getScene() == null) {
            throw new IllegalArgumentException("Node must already be attached to a scene.");
        }

        Stage owner = (Stage) anyNodeInScene.getScene().getWindow();
        return show(owner, title, message);
    }

    public static boolean show(Stage owner, String title, String message) {
        final boolean[] result = {false};

        Stage dialog = new Stage(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        StackPane overlayRoot = new StackPane();
        overlayRoot.setBackground(Background.EMPTY);
        overlayRoot.setStyle("-fx-background-color: transparent;");
        overlayRoot.setPadding(new Insets(30));

        StackPane shell = new StackPane();
        shell.setMaxWidth(460);
        shell.setPrefWidth(460);
        shell.setMinWidth(420);

        VBox box = new VBox(16);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(18, 18, 16, 18));
        box.setStyle(
                "-fx-background-color: "
              + "linear-gradient(to bottom right, rgba(48,10,18,0.985), rgba(30,8,14,0.99)), "
              + "radial-gradient(center 20% 10%, radius 70%, rgba(255,92,116,0.14), transparent 68%);"
              + "-fx-background-radius: 22;"
              + "-fx-border-color: rgba(255, 99, 126, 0.58);"
              + "-fx-border-width: 1.25;"
              + "-fx-border-radius: 22;"
        );

        DropShadow shadow1 = new DropShadow();
        shadow1.setRadius(28);
        shadow1.setSpread(0.10);
        shadow1.setOffsetY(10);
        shadow1.setColor(Color.rgb(255, 86, 110, 0.24));

        DropShadow shadow2 = new DropShadow();
        shadow2.setRadius(40);
        shadow2.setSpread(0.06);
        shadow2.setOffsetY(0);
        shadow2.setColor(Color.rgb(255, 120, 82, 0.12));
        shadow2.setInput(shadow1);

        box.setEffect(shadow2);

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox titleWrap = new VBox(4);
        titleWrap.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title == null ? "Confirm Action" : title);
        titleLabel.setStyle(
                "-fx-text-fill: white;"
              + "-fx-font-size: 18px;"
              + "-fx-font-weight: bold;"
        );

        Label subLabel = new Label("WARNING CONFIRMATION");
        subLabel.setStyle(
                "-fx-text-fill: #ff9cab;"
              + "-fx-font-size: 11px;"
              + "-fx-font-weight: 800;"
              + "-fx-letter-spacing: 1px;"
        );

        titleWrap.getChildren().addAll(subLabel, titleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topBar.getChildren().addAll(titleWrap, spacer);

        Line neonLine = new Line(0, 0, 380, 0);
        neonLine.setStroke(Color.rgb(255, 99, 126, 0.75));
        neonLine.setStrokeWidth(1.4);

        Label msgLabel = new Label(message == null ? "Are you sure?" : message);
        msgLabel.setWrapText(true);
        msgLabel.setStyle(
                "-fx-text-fill: #d8ecff;"
              + "-fx-font-size: 13px;"
              + "-fx-line-spacing: 2px;"
        );

        StackPane messagePane = new StackPane(msgLabel);
        messagePane.setAlignment(Pos.CENTER_LEFT);
        messagePane.setPadding(new Insets(12, 12, 12, 12));
        messagePane.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, rgba(255,255,255,0.040), rgba(255,255,255,0.022));"
              + "-fx-background-radius: 16;"
              + "-fx-border-color: rgba(255,124,138,0.28);"
              + "-fx-border-width: 1;"
              + "-fx-border-radius: 16;"
        );

        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = createButton("Cancel",
                "-fx-background-color: linear-gradient(to bottom, rgba(255,255,255,0.08), rgba(255,255,255,0.04));"
              + "-fx-border-color: rgba(255,156,171,0.34);"
              + "-fx-text-fill: #eaf6ff;"
              + "-fx-effect: dropshadow(gaussian, rgba(255,92,116,0.18), 10, 0.15, 0, 2);");

        Button confirmBtn = createButton("Confirm",
                "-fx-background-color: linear-gradient(to right, #ff5b6c, #ff8a57);"
              + "-fx-border-color: rgba(255,233,238,0.46);"
              + "-fx-text-fill: white;"
              + "-fx-effect: dropshadow(gaussian, rgba(255,96,120,0.38), 14, 0.22, 0, 4);");

        makeButtonReactive(cancelBtn);
        makeButtonReactive(confirmBtn);

        cancelBtn.setOnAction(e -> {
            result[0] = false;
            playClose(dialog, overlayRoot);
        });

        confirmBtn.setOnAction(e -> {
            result[0] = true;
            playClose(dialog, overlayRoot);
        });

        buttonBar.getChildren().addAll(cancelBtn, confirmBtn);

        box.getChildren().addAll(topBar, neonLine, messagePane, buttonBar);

        shell.getChildren().add(box);

        Rectangle scanline1 = createScanline(340, Color.rgb(255, 99, 126, 0.12));
        Rectangle scanline2 = createScanline(260, Color.rgb(255, 138, 87, 0.10));
        scanline1.setTranslateY(-90);
        scanline2.setTranslateY(80);

        shell.getChildren().addAll(scanline1, scanline2);
        overlayRoot.getChildren().add(shell);

        Scene scene = new Scene(overlayRoot);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);

        overlayRoot.setOpacity(0);
        shell.setOpacity(0);
        shell.setScaleX(0.88);
        shell.setScaleY(0.88);
        shell.setTranslateY(26);

        attachCloseOnBackgroundClick(overlayRoot, shell, () -> {
            result[0] = false;
            playClose(dialog, overlayRoot);
        });

        playOpen(overlayRoot, shell, titleLabel, scanline1, scanline2);

        dialog.showAndWait();
        return result[0];
    }

    private static Button createButton(String text, String extraStyle) {
        Button btn = new Button(text);
        btn.setPrefHeight(38);
        btn.setMinHeight(38);
        btn.setPrefWidth(108);
        btn.setStyle(
                "-fx-background-radius: 12;"
              + "-fx-border-radius: 12;"
              + "-fx-border-width: 1.0;"
              + "-fx-font-size: 13px;"
              + "-fx-font-weight: bold;"
              + "-fx-cursor: hand;"
              + extraStyle
        );
        return btn;
    }

    private static Rectangle createScanline(double width, Color color) {
        Rectangle r = new Rectangle(width, 2);
        r.setFill(color);
        r.setManaged(false);
        r.setMouseTransparent(true);
        return r;
    }

    private static void makeButtonReactive(Button btn) {
        ScaleTransition hoverIn = new ScaleTransition(Duration.millis(120), btn);
        hoverIn.setToX(1.05);
        hoverIn.setToY(1.05);

        ScaleTransition hoverOut = new ScaleTransition(Duration.millis(120), btn);
        hoverOut.setToX(1.0);
        hoverOut.setToY(1.0);

        btn.setOnMouseEntered(e -> hoverIn.playFromStart());
        btn.setOnMouseExited(e -> hoverOut.playFromStart());

        btn.setOnMousePressed(e -> {
            btn.setScaleX(0.97);
            btn.setScaleY(0.97);
        });

        btn.setOnMouseReleased(e -> {
            btn.setScaleX(1.04);
            btn.setScaleY(1.04);
        });
    }

    private static void attachCloseOnBackgroundClick(StackPane overlayRoot, StackPane shell, Runnable action) {
        overlayRoot.setOnMouseClicked(e -> {
            Parent target = e.getPickResult().getIntersectedNode() instanceof Parent
                    ? (Parent) e.getPickResult().getIntersectedNode()
                    : null;

            if (target == null || !isInside(target, shell)) {
                action.run();
            }
        });
    }

    private static boolean isInside(Node node, Node ancestor) {
        Node current = node;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static void playOpen(StackPane overlayRoot,
                                 StackPane shell,
                                 Node title,
                                 Rectangle scanline1,
                                 Rectangle scanline2) {

        FadeTransition fadeBg = new FadeTransition(Duration.millis(180), overlayRoot);
        fadeBg.setFromValue(0);
        fadeBg.setToValue(1);

        FadeTransition fadeShell = new FadeTransition(Duration.millis(220), shell);
        fadeShell.setFromValue(0);
        fadeShell.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(260), shell);
        scale.setFromX(0.88);
        scale.setFromY(0.88);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.SPLINE(0.20, 0.90, 0.20, 1.00));

        TranslateTransition slide = new TranslateTransition(Duration.millis(260), shell);
        slide.setFromY(26);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.SPLINE(0.20, 0.90, 0.20, 1.00));

        TranslateTransition titleGlitch1 = new TranslateTransition(Duration.millis(90), title);
        titleGlitch1.setByX(1.5);
        titleGlitch1.setCycleCount(4);
        titleGlitch1.setAutoReverse(true);

        Timeline scan1 = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(scanline1.translateYProperty(), -95)),
                new KeyFrame(Duration.seconds(1.7), new KeyValue(scanline1.translateYProperty(), 95))
        );
        scan1.setCycleCount(Animation.INDEFINITE);

        Timeline scan2 = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(scanline2.translateYProperty(), 90)),
                new KeyFrame(Duration.seconds(2.0), new KeyValue(scanline2.translateYProperty(), -90))
        );
        scan2.setCycleCount(Animation.INDEFINITE);

        ParallelTransition open = new ParallelTransition(fadeBg, fadeShell, scale, slide);
        open.setOnFinished(e -> {
            titleGlitch1.playFromStart();
            scan1.play();
            scan2.play();
            shell.getProperties().put("scan1", scan1);
            shell.getProperties().put("scan2", scan2);
        });
        open.play();
    }

    private static void playClose(Stage dialog, StackPane overlayRoot) {
        Scene scene = dialog.getScene();
        Node shell = ((StackPane) scene.getRoot()).getChildren().get(0);

        stopIfPresent(shell, "scan1");
        stopIfPresent(shell, "scan2");

        FadeTransition fadeBg = new FadeTransition(Duration.millis(140), overlayRoot);
        fadeBg.setToValue(0);

        FadeTransition fadeShell = new FadeTransition(Duration.millis(140), shell);
        fadeShell.setToValue(0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(160), shell);
        scale.setToX(0.92);
        scale.setToY(0.92);

        TranslateTransition slide = new TranslateTransition(Duration.millis(160), shell);
        slide.setToY(18);

        ParallelTransition close = new ParallelTransition(fadeBg, fadeShell, scale, slide);
        close.setOnFinished(e -> dialog.close());
        close.play();
    }

    private static void stopIfPresent(Node node, String key) {
        Object anim = node.getProperties().get(key);
        if (anim instanceof Animation) {
            ((Animation) anim).stop();
        }
    }
}
