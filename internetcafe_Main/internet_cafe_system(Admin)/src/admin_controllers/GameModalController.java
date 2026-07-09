package admin_controllers;

import animation.PixelMotion;
import member_controllers.ClientInterface;
import java.net.URL;
import java.util.ResourceBundle;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

public class GameModalController implements Initializable, InitializableController {

    @FXML private Label modalTitleLabel;
    @FXML private ImageView imagePreview;
    @FXML private Label imagePlaceholder;
    @FXML private TextField imagePathField;
    @FXML private TextField nameField;
    @FXML private TextField gameIdField;

    private GameController parentController;
    private GameItem editingGame;
    private String savedImageName;
    private String originalGameId;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    
    private static final Path IMAGE_DIR = Paths.get(
        System.getProperty("user.dir"), "src", "dbimages"
    );

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Platform.runLater(() -> {
            if (modalTitleLabel != null && modalTitleLabel.getScene() != null
                    && modalTitleLabel.getScene().getRoot() instanceof javafx.scene.Parent) {
                javafx.scene.Parent parent = (javafx.scene.Parent) modalTitleLabel.getScene().getRoot();
                PixelMotion.applyTo(parent);
                PixelMotion.playWindowIntro(parent, false);
            }
        });
    }

    public void setParentController(GameController parentController) {
        this.parentController = parentController;
    }

    public void initForAdd() {
        editingGame = null;
        originalGameId = null;
        modalTitleLabel.setText("Add Game");
        nameField.clear();
        gameIdField.clear();
        imagePathField.clear();
        imagePreview.setImage(null);
        imagePlaceholder.setVisible(true);
        savedImageName = null;
    }

    public void initForEdit(GameItem game) {
        this.editingGame = game;
        this.originalGameId = game.getGameId();
        modalTitleLabel.setText("Edit Game");
        nameField.setText(game.getTitle() == null ? "" : game.getTitle());
        gameIdField.setText(game.getGameId() == null ? "" : game.getGameId());
        savedImageName = game.getImagePath();
        imagePathField.setText(game.getImagePath() == null ? "" : game.getImagePath());
        previewImage(game.getImagePath());
    }

    @FXML
    private void chooseImage(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif")
        );

        File file = fc.showOpenDialog(((Node) event.getSource()).getScene().getWindow());
        if (file != null) {
            try {
                File dir = IMAGE_DIR.toFile();
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                String fileName = System.currentTimeMillis() + "_" + file.getName();
                Path dest = IMAGE_DIR.resolve(fileName);

                Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

                savedImageName = fileName;
                imagePathField.setText(fileName);
                previewImageFile(dest.toFile());

            } catch (IOException e) {
                showError("Image Error", e.getMessage());
            }
        }
    }

    @FXML
    private void resetImage(ActionEvent event) {
        savedImageName = null;
        imagePathField.clear();
        imagePreview.setImage(null);
        imagePlaceholder.setVisible(true);
    }

    @FXML
    private void saveGame(ActionEvent event) {
        if (con == null) {
            showError("Database Error", "Database connection not available");
            return;
        }

        String gameId = gameIdField.getText() == null ? "" : gameIdField.getText().trim();
        String title = nameField.getText() == null ? "" : nameField.getText().trim();

        if (gameId.isEmpty()) {
            showError("Input Error", "Game ID is required.");
            return;
        }
        if (title.isEmpty()) {
            showError("Input Error", "Game title is required.");
            return;
        }

        boolean isEdit = editingGame != null;
        String sql = isEdit
                ? "UPDATE internet_cafe.game SET game_id = ?, game_name = ?, image = ? WHERE game_id = ?"
                : "INSERT INTO internet_cafe.game (game_id, game_name, image) VALUES (?, ?, ?)";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, gameId);
            ps.setString(2, title);
            ps.setString(3, savedImageName);
            if (isEdit) {
                ps.setString(4, originalGameId);
            }
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

            closeModal(event);

        } catch (Exception e) {
            showError("Save Error", e.getMessage());
        }
    }

    @FXML
    private void closeModal(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    private void previewImage(String imageName) {
        try {
            if (imageName == null || imageName.isBlank()) {
                imagePreview.setImage(null);
                imagePlaceholder.setVisible(true);
                return;
            }

            File file = IMAGE_DIR.resolve(imageName).toFile();
            if (file.exists()) {
                previewImageFile(file);
                return;
            }

            if (imageName.startsWith("file:") || imageName.startsWith("http://") || imageName.startsWith("https://")) {
                imagePreview.setImage(new Image(imageName, true));
            }

            imagePlaceholder.setVisible(imagePreview.getImage() == null);

        } catch (Exception e) {
            imagePreview.setImage(null);
            imagePlaceholder.setVisible(true);
        }
    }
    
    private void previewImageFile(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            Image img = new Image(new ByteArrayInputStream(bytes));
            imagePreview.setImage(img);
            imagePlaceholder.setVisible(false);
        } catch (IOException e) {
            imagePreview.setImage(null);
            imagePlaceholder.setVisible(true);
        }
    }

    private void showError(String title, String message) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(nameField, title,
                message == null ? "Unknown error" : message,
                PixelMotion.ToastType.ERROR));
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