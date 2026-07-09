package admin_controllers;

import animation.PixelMotion;
import animation.CyberConfirmBox;
import member_controllers.ClientInterface;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

public class GameCardController implements InitializableController {

    @FXML private StackPane cardShell;
    @FXML private StackPane imageWrap;
    @FXML private ImageView gameImage;
    @FXML private Label imagePlaceholder;
    @FXML private Label titleLabel;
    @FXML private Label infoValueLabel;
    @FXML private javafx.scene.control.Button editBtn;
    @FXML private javafx.scene.control.Button removeBtn;

    private GameItem currentGame;
    private GameController parentController;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    
    private static final Path IMAGE_DIR = Paths.get(
        System.getProperty("user.dir"), "src", "dbimages"
    );

    @FXML
    private void initialize() {
        if (cardShell != null) PixelMotion.applyUltraCardHover(cardShell);
        gameImage.setSmooth(true);
        gameImage.setPreserveRatio(false);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        clip.widthProperty().bind(gameImage.fitWidthProperty());
        clip.heightProperty().bind(gameImage.fitHeightProperty());
        gameImage.setClip(clip);
    }

    public void setParentController(GameController parentController) {
        this.parentController = parentController;
    }

    public void setData(GameItem game) {
        this.currentGame = game;
        titleLabel.setText(game.getTitle() == null || game.getTitle().isBlank() ? "Untitled Game" : game.getTitle());
        infoValueLabel.setText(game.getGameId() == null || game.getGameId().isBlank() ? "-" : game.getGameId());
        loadImage(game.getImagePath());
    }

    private void loadImage(String imageName) {
        gameImage.setImage(null);
        gameImage.setViewport(null);
        imagePlaceholder.setVisible(true);

        if (imageName == null || imageName.isBlank()) {
            return;
        }

        try {
            Path imagePath = IMAGE_DIR.resolve(imageName);
            File imageFile = imagePath.toFile();
            
            if (imageFile.exists()) {
                byte[] imageBytes = Files.readAllBytes(imagePath);
                Image image = new Image(new ByteArrayInputStream(imageBytes));
                
                if (!image.isError()) {
                    gameImage.setFitWidth(252);
                    gameImage.setFitHeight(138);
                    gameImage.setImage(image);
                    applyCoverViewport(image);
                    imagePlaceholder.setVisible(false);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load image from dbimages: " + e.getMessage());
        }

        try {
            if (imageName.startsWith("http://") || imageName.startsWith("https://") || imageName.startsWith("file:")) {
                Image image = new Image(imageName, false);
                if (!image.isError()) {
                    gameImage.setFitWidth(252);
                    gameImage.setFitHeight(138);
                    gameImage.setImage(image);
                    applyCoverViewport(image);
                    imagePlaceholder.setVisible(false);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void applyCoverViewport(Image image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            gameImage.setViewport(null);
            return;
        }

        double boxWidth = gameImage.getFitWidth();
        double boxHeight = gameImage.getFitHeight();
        double targetRatio = boxWidth / boxHeight;
        double imageRatio = image.getWidth() / image.getHeight();

        Rectangle2D viewport;

        if (imageRatio > targetRatio) {
            double cropWidth = image.getHeight() * targetRatio;
            double x = (image.getWidth() - cropWidth) / 2.0;
            viewport = new Rectangle2D(x, 0, cropWidth, image.getHeight());
        } else {
            double cropHeight = image.getWidth() / targetRatio;
            double y = (image.getHeight() - cropHeight) / 2.0;
            viewport = new Rectangle2D(0, y, image.getWidth(), cropHeight);
        }

        gameImage.setViewport(viewport);
    }

    @FXML
    private void edit(ActionEvent event) {
        if (parentController == null || currentGame == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/game_modal.fxml"));
            Parent root = loader.load();

            GameModalController controller = loader.getController();
            controller.setParentController(parentController);
            controller.initForEdit(currentGame);
            
            if (controller instanceof InitializableController) {
                ((InitializableController) controller).setServer(server);
                ((InitializableController) controller).setClient(client);
                ((InitializableController) controller).setDatabaseConnection(con);
            }

            StackPane hostStack = parentController != null ? parentController.getContentStack() : null;
            if (hostStack != null) {
                PixelMotion.showOverlayInStack(hostStack, root, false, parentController::refreshGames);
            } else {
                PixelMotion.playWindowIntro(root, false);
            }
        } catch (IOException e) {
            showError("Modal Error", e.getMessage());
        }
    }

    @FXML
    private void remove(ActionEvent event) {
        if (currentGame == null || currentGame.getGameId() == null || currentGame.getGameId().isBlank()) {
            showError("Remove Error", "This game does not have a valid Game ID.");
            return;
        }

        boolean confirmed = CyberConfirmBox.show(
                removeBtn,
                "Delete Game",
                "Are you sure you want to remove \"" + currentGame.getTitle() + "\"?\n\nThis action cannot be undone."
        );

        if (!confirmed) return;

        if (con == null) {
            showError("Database Error", "Database connection not available");
            return;
        }

        String sql = "DELETE FROM internet_cafe.game WHERE game_id = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, currentGame.getGameId());
            ps.executeUpdate();

            if (parentController != null) {
                parentController.refreshGames();
            }

            if (server != null) {
                try {
                    server.notifyGamesChanged();
                } catch (java.rmi.RemoteException e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            showError("Remove Error", e.getMessage());
        }
    }

    private void showError(String title, String message) {
        javafx.application.Platform.runLater(() ->
            animation.PixelMotion.toastGlitch(titleLabel, title,
                message == null ? "Unknown error" : message,
                animation.PixelMotion.ToastType.ERROR));
    }
    
    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
    }
    
    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
    }
    
    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
    }
}