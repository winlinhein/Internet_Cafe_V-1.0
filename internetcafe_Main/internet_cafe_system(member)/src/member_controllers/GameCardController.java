package member_controllers;

import admin_controllers.ServerInterface;
import animation.PixelMotion;
import java.io.ByteArrayInputStream;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class GameCardController {

    @FXML private VBox cardRoot;
    @FXML private StackPane imageWrap;
    @FXML private ImageView gameImage;
    @FXML private Label gameTitle;
    @FXML private Button playButton;

    private Game game;
    private ServerInterface server;

    private static final Map<String, byte[]> imageCache = new ConcurrentHashMap<>();

    public static void clearImageCache() {
        imageCache.clear();
    }

    public void setServer(ServerInterface server) {
        this.server = server;
    }

    public void initialize() {
        PixelMotion.installCardHover(cardRoot, gameImage, null);
        installImageClip();
        gameImage.setSmooth(true);
        gameImage.setPreserveRatio(false);
    }

    private void installImageClip() {
        if (imageWrap == null) return;
        Rectangle clip = new Rectangle();
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        clip.widthProperty().bind(imageWrap.widthProperty().subtract(2));
        clip.heightProperty().bind(imageWrap.heightProperty().subtract(2));
        imageWrap.setClip(clip);
    }

    public void setGameData(Game game) {
        this.game = game;
        gameTitle.setText(game.getTitle());
        loadGameImage(game.getImagePath());
    }

    private void loadGameImage(String imageFileName) {
        if (imageFileName == null || imageFileName.isBlank()) {
            gameImage.setImage(null);
            return;
        }

        byte[] cached = imageCache.get(imageFileName);
        if (cached != null) {
            setImageFromBytes(cached);
            return;
        }

        if (server == null) {
            gameImage.setImage(null);
            return;
        }

        new Thread(() -> {
            try {
                byte[] imageBytes = server.getImageBytes(imageFileName);
                if (imageBytes != null && imageBytes.length > 0) {
                    imageCache.put(imageFileName, imageBytes);
                    Platform.runLater(() -> setImageFromBytes(imageBytes));
                } else {
                    Platform.runLater(() -> gameImage.setImage(null));
                }
            } catch (RemoteException e) {
                e.printStackTrace();
                Platform.runLater(() -> gameImage.setImage(null));
            }
        }).start();
    }

    private void setImageFromBytes(byte[] bytes) {
        Image img = new Image(new ByteArrayInputStream(bytes));
        gameImage.setImage(img);
        if (img.getProgress() >= 1.0) {
            applyCoverViewport(img);
        } else {
            img.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV.doubleValue() >= 1.0) applyCoverViewport(img);
            });
        }
    }

    private void applyCoverViewport(Image image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            gameImage.setViewport(null);
            return;
        }

        double viewportWidth = gameImage.getFitWidth() > 0 ? gameImage.getFitWidth() : 182;
        double viewportHeight = gameImage.getFitHeight() > 0 ? gameImage.getFitHeight() : 194;
        double targetAspect = viewportWidth / viewportHeight;
        double imageAspect = image.getWidth() / image.getHeight();

        Rectangle2D viewport;
        if (imageAspect > targetAspect) {
            double cropWidth = image.getHeight() * targetAspect;
            double x = (image.getWidth() - cropWidth) / 2.0;
            viewport = new Rectangle2D(x, 0, cropWidth, image.getHeight());
        } else {
            double cropHeight = image.getWidth() / targetAspect;
            double y = (image.getHeight() - cropHeight) / 2.0;
            viewport = new Rectangle2D(0, y, image.getWidth(), cropHeight);
        }

        gameImage.setViewport(viewport);
    }

    @FXML
    private void handlePlayAction() {
        if (game == null) {
            return;
        }

        try {
            if (game.getSteamGameId() != null && !game.getSteamGameId().isBlank()) {
                new ProcessBuilder("cmd", "/c", "start", "", "steam://rungameid/" + game.getSteamGameId()).start();
                return;
            }

            if (game.getLocalExePath() != null && !game.getLocalExePath().isBlank()) {
                new ProcessBuilder(game.getLocalExePath()).start();
                return;
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
}