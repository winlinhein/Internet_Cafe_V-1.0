package animation;

import animation.PixelMotion;
import animation.UniversalGlitch;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class Login {

    private static Timeline loadingDots;
    private static Timeline loadingSubtextLoop;

    private Login() {
    }

    public static void installVisuals(StackPane root,
                                      ImageView bgGif,
                                      VBox logobox,
                                      ImageView logo,
                                      Label welcome,
                                      Label hint,
                                      Region scanlineOverlay,
                                      TextField usernameField,
                                      PasswordField passwordField,
                                      Label enterLabel) {
        bindBackground(root, bgGif);
        installFieldFx(usernameField, passwordField);
        installEnterHover(enterLabel);
        installLogoHover(logo);
        startIdleEffects(logobox, logo, welcome, hint, scanlineOverlay);
        startIntro(logobox, welcome, hint);
    }

    public static void animateIntroOut(VBox logobox,
                                       Label welcome,
                                       Label hint,
                                       Region dimOverlay,
                                       Region vignetteOverlay,
                                       Runnable after) {
        FadeTransition dimBoost = fadeTo(dimOverlay, 420, 1.0);
        FadeTransition vignetteBoost = fadeTo(vignetteOverlay, 420, 1.0);

        ParallelTransition hidePrompt = new ParallelTransition(
                fadeOutUp(logobox, -18, 300),
                fadeOutUp(welcome, -16, 260),
                fadeOutUp(hint, -10, 220)
        );

        ParallelTransition all = new ParallelTransition(dimBoost, vignetteBoost, hidePrompt);
        all.setOnFinished(e -> {
            collapseNode(logobox);
            collapseNode(welcome);
            collapseNode(hint);
            if (after != null) {
                after.run();
            }
        });
        all.play();
    }

    public static void showLoadingBox(VBox loadingBox, Label loadingText, Label loadingSubtext) {
        if (loadingBox == null) {
            return;
        }
        loadingBox.setManaged(true);
        loadingBox.setVisible(true);
        loadingBox.setOpacity(0.0);
        loadingBox.setTranslateY(18);
        loadingBox.setScaleX(0.92);
        loadingBox.setScaleY(0.92);

        ParallelTransition reveal = new ParallelTransition(
                fadeInRise(loadingBox, 18, 340),
                pulse(loadingBox, 1.0, 1.025, 260)
        );
        reveal.play();

        startLoadingText(loadingText);
        startLoadingSubtext(loadingSubtext);
    }

    public static void hideLoadingBox(VBox loadingBox, Runnable after) {
        stopLoadingLoops();
        ParallelTransition hide = fadeOutUp(loadingBox, -14, 240);
        hide.setOnFinished(e -> {
            collapseNode(loadingBox);
            if (after != null) {
                after.run();
            }
        });
        hide.play();
    }

    public static void showLoginBox(VBox loginBox,
                                    TextField usernameField,
                                    PasswordField passwordField,
                                    Label enterLabel) {
        if (loginBox == null) {
            return;
        }
        loginBox.setManaged(true);
        loginBox.setVisible(true);

        ParallelTransition revealBox = new ParallelTransition(
                fadeInRise(loginBox, 28, 460),
                pulse(loginBox, 1.0, 1.03, 380)
        );

        SequentialTransition stagedFields = new SequentialTransition(
                popIn(usernameField, 16, 160),
                popIn(passwordField, 16, 120),
                popIn(enterLabel, 18, 120)
        );

        revealBox.play();
        stagedFields.play();
    }

    private static void bindBackground(StackPane root, ImageView bgGif) {
        if (root == null || bgGif == null) {
            return;
        }
        bgGif.fitWidthProperty().bind(root.widthProperty());
        bgGif.fitHeightProperty().bind(root.heightProperty());
    }

    private static void startIdleEffects(VBox logobox,
                                         ImageView logo,
                                         Label welcome,
                                         Label hint,
                                         Region scanlineOverlay) {
        if (welcome != null) {
            UniversalGlitch.attach(welcome).start();
        }
        if (logo != null) {
            UniversalGlitch.attach(logo).start();
        }
        if (logobox != null) {
            TranslateTransition logoFloat = new TranslateTransition(Duration.millis(2600), logobox);
            logoFloat.setFromY(0);
            logoFloat.setToY(-10);
            logoFloat.setInterpolator(Interpolator.EASE_BOTH);
            logoFloat.setAutoReverse(true);
            logoFloat.setCycleCount(Animation.INDEFINITE);
            logoFloat.play();
        }
        if (hint != null) {
            FadeTransition hintPulse = new FadeTransition(Duration.millis(1500), hint);
            hintPulse.setFromValue(0.45);
            hintPulse.setToValue(1.0);
            hintPulse.setAutoReverse(true);
            hintPulse.setCycleCount(Animation.INDEFINITE);
            hintPulse.play();
        }
        if (scanlineOverlay != null) {
            TranslateTransition scanMove = new TranslateTransition(Duration.millis(2200), scanlineOverlay);
            scanMove.setFromY(-18);
            scanMove.setToY(18);
            scanMove.setAutoReverse(true);
            scanMove.setCycleCount(Animation.INDEFINITE);
            scanMove.play();
        }
    }

    private static void startIntro(VBox logobox, Label welcome, Label hint) {
        PixelMotion.playEntrance(logobox, -36, 680, 0);
        PixelMotion.playEntrance(welcome, 16, 620, 90);
        PixelMotion.playEntrance(hint, 22, 620, 180);
    }

    private static void startLoadingText(Label label) {
        if (label == null) {
            return;
        }
        loadingDots = new Timeline(
                new KeyFrame(Duration.ZERO, e -> label.setText("Loading")),
                new KeyFrame(Duration.millis(240), e -> label.setText("Loading.")),
                new KeyFrame(Duration.millis(480), e -> label.setText("Loading..")),
                new KeyFrame(Duration.millis(720), e -> label.setText("Loading...")),
                new KeyFrame(Duration.millis(960), e -> label.setText("Loading....")),
                new KeyFrame(Duration.millis(1200), e -> label.setText("Loading....."))
        );
        loadingDots.setCycleCount(Animation.INDEFINITE);
        loadingDots.play();
    }

    private static void startLoadingSubtext(Label label) {
        if (label == null) {
            return;
        }
        loadingSubtextLoop = new Timeline(
                new KeyFrame(Duration.ZERO, e -> label.setText("Waiting for admin unlock")),
                new KeyFrame(Duration.seconds(1.2), e -> label.setText("Checking station status")),
                new KeyFrame(Duration.seconds(2.4), e -> label.setText("Awaiting permission from admin")),
                new KeyFrame(Duration.seconds(3.6), e -> label.setText("Station remains locked"))
        );
        loadingSubtextLoop.setCycleCount(Animation.INDEFINITE);
        loadingSubtextLoop.play();
    }

    private static void stopLoadingLoops() {
        if (loadingDots != null) {
            loadingDots.stop();
            loadingDots = null;
        }
        if (loadingSubtextLoop != null) {
            loadingSubtextLoop.stop();
            loadingSubtextLoop = null;
        }
    }

    private static void collapseNode(Node node) {
        if (node == null) {
            return;
        }
        node.setManaged(false);
        node.setVisible(false);
    }

    private static FadeTransition fadeTo(Node node, int ms, double toValue) {
        if (node == null) {
            return new FadeTransition();
        }
        FadeTransition fade = new FadeTransition(Duration.millis(ms), node);
        fade.setFromValue(node.getOpacity() <= 0 ? 1.0 : node.getOpacity());
        fade.setToValue(toValue);
        return fade;
    }

    private static ParallelTransition fadeOutUp(Node node, double toY, int ms) {
        if (node == null) {
            return new ParallelTransition();
        }
        TranslateTransition move = new TranslateTransition(Duration.millis(ms), node);
        move.setToY(toY);
        move.setInterpolator(Interpolator.EASE_BOTH);

        FadeTransition fade = new FadeTransition(Duration.millis(ms), node);
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_BOTH);

        return new ParallelTransition(move, fade);
    }

    private static ParallelTransition fadeInRise(Node node, double fromY, int ms) {
        if (node == null) {
            return new ParallelTransition();
        }
        node.setOpacity(0.0);
        node.setTranslateY(fromY);
        node.setScaleX(0.92);
        node.setScaleY(0.92);

        FadeTransition fade = new FadeTransition(Duration.millis(ms), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition move = new TranslateTransition(Duration.millis(ms), node);
        move.setFromY(fromY);
        move.setToY(0.0);
        move.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(ms), node);
        scale.setFromX(0.92);
        scale.setFromY(0.92);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        return new ParallelTransition(fade, move, scale);
    }

    private static ParallelTransition popIn(Node node, double fromY, int ms) {
        if (node == null) {
            return new ParallelTransition();
        }
        node.setOpacity(0.0);
        node.setTranslateY(fromY);
        node.setScaleX(0.96);
        node.setScaleY(0.96);

        FadeTransition fade = new FadeTransition(Duration.millis(ms), node);
        fade.setToValue(1.0);

        TranslateTransition move = new TranslateTransition(Duration.millis(ms), node);
        move.setToY(0.0);
        move.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(ms), node);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        return new ParallelTransition(fade, move, scale);
    }

    private static ParallelTransition pulse(Node node, double fromScale, double toScale, int ms) {
        if (node == null) {
            return new ParallelTransition();
        }
        ScaleTransition scale = new ScaleTransition(Duration.millis(ms), node);
        scale.setFromX(fromScale);
        scale.setFromY(fromScale);
        scale.setToX(toScale);
        scale.setToY(toScale);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.setInterpolator(Interpolator.EASE_BOTH);
        return new ParallelTransition(scale);
    }

    private static void installFieldFx(TextField usernameField, PasswordField passwordField) {
        PixelMotion.installSearchFieldFX(null, usernameField, 380, 380);
        PixelMotion.installSearchFieldFX(null, passwordField, 380, 380);
    }

    private static void installLogoHover(ImageView logo) {
        PixelMotion.installImageHover(logo, 1.05);
    }

    private static void installEnterHover(Label target) {
        if (target == null) {
            return;
        }
        target.setOnMouseEntered(e -> {
            Timeline t = new Timeline(
                    new KeyFrame(Duration.millis(170),
                            new KeyValue(target.scaleXProperty(), 1.03, Interpolator.EASE_OUT),
                            new KeyValue(target.scaleYProperty(), 1.03, Interpolator.EASE_OUT),
                            new KeyValue(target.translateXProperty(), 6, Interpolator.EASE_OUT)
                    )
            );
            t.play();
        });
        target.setOnMouseExited(e -> {
            Timeline t = new Timeline(
                    new KeyFrame(Duration.millis(170),
                            new KeyValue(target.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                            new KeyValue(target.scaleYProperty(), 1.0, Interpolator.EASE_OUT),
                            new KeyValue(target.translateXProperty(), 0, Interpolator.EASE_OUT)
                    )
            );
            t.play();
        });
    }
}
