package admin_controllers;

import animation.PixelMotion;
import database.DatabaseConnection;
import static admin_controllers.Admin_Main.stage;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import member_controllers.ClientInterface;
import java.rmi.registry.Registry;

public class GameController implements Initializable, InitializableController {

    @FXML private BorderPane root;
    @FXML private TextField topSearchField;
    @FXML private Button btnNotifications;
    @FXML private Label notifBadge;
    @FXML private Button btnSettings;
    @FXML private Button btnLogout;
    @FXML private VBox sidebar;
    @FXML private Label lblStatus;
    @FXML private Label lblShift;
    @FXML private Label lblClock;
    @FXML private TextField searchField;
    @FXML private Button addGameBtn;
    @FXML private FlowPane gameFlow;
    @FXML private StackPane contentStack;

    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private Registry registry;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (con != null) {
                loadGames(newValue);
            }
        });

        if (con != null) {
            Platform.runLater(() -> loadGames(""));
        }
    }

    private void switchScene(String fxmlPath) throws IOException {
        Object prevCtrl = Admin_Main.getActiveController();
        if (prevCtrl instanceof AdminCallback && prevCtrl != this) {
            if (prevCtrl instanceof CustomersController) {
                ((CustomersController) prevCtrl).unregisterCallback();
            } else if (prevCtrl instanceof Admin_dashboardController) {
                ((Admin_dashboardController) prevCtrl).unregisterCallback();
            } else if (prevCtrl instanceof PCViewController) {
                ((PCViewController) prevCtrl).unregisterCallback();
            }
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent rootNode = loader.load();
        PixelMotion.applyTo(rootNode);

        Object controller = loader.getController();
        if (controller instanceof InitializableController) {
            InitializableController ic = (InitializableController) controller;
            ic.setServer(server);
            ic.setClient(client);
            ic.setDatabaseConnection(con);
        
            if (controller instanceof PCViewController && server instanceof ServerImpl) {
                ((ServerImpl) server).setPCController((PCViewController) controller);
            }
        }

        if (stage.getScene() == null) {
            stage.setScene(new Scene(rootNode));
        } else {
            stage.getScene().setRoot(rootNode);
        }
        stage.setFullScreen(true);

        Admin_Main.setActiveController(controller);
    }

    private void loadGames(String query) {
        if (con == null) {
            return;
        }

        new Thread(() -> {
            String sql = "SELECT game_id, game_name, image FROM internet_cafe.game "
                        + "WHERE game_name LIKE ? OR CAST(game_id AS CHAR) LIKE ? "
                        + "ORDER BY game_name";

            List<Node> cards = new ArrayList<>(); 
    
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                String like = "%" + (query == null ? "" : query.trim()) + "%";
                ps.setString(1, like);
                ps.setString(2, like);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        GameItem item = new GameItem(
                                rs.getString("game_id"),
                                rs.getString("game_name"),
                                rs.getString("image")
                        );

                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/game_card.fxml"));
                        Node card = loader.load(); 
                
                        GameCardController controller = loader.getController();
                        if (controller != null) {
                            controller.setParentController(this);
                            controller.setData(item);
                        
                            if (controller instanceof InitializableController) {
                                ((InitializableController) controller).setServer(server);
                                ((InitializableController) controller).setClient(client);
                                ((InitializableController) controller).setDatabaseConnection(con);
                            }
                        }
                        cards.add(card);
                    }
                }

                Platform.runLater(() -> {
                    gameFlow.getChildren().setAll(cards);
                    for (int i = 0; i < cards.size(); i++) {
                        PixelMotion.playGameCardSpawn(cards.get(i), i);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showError("Load Error", e.getMessage()));
            }
        }).start();
    }

    public void refreshGames() {
        if (con == null) {
            return;
        }
        loadGames(searchField == null ? "" : searchField.getText().trim());
    }
    
    @FXML
    void handleMyProfile(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/MyProfile.fxml"));
            Parent root = loader.load();

            MyProfileController profileController = loader.getController();

            if (profileController instanceof InitializableController) {
                ((InitializableController) profileController).setServer(server);
                ((InitializableController) profileController).setClient(client);
                ((InitializableController) profileController).setDatabaseConnection(con);
            }

            PixelMotion.showOverlayInStack(contentStack, root, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onLogout(ActionEvent event) throws IOException {
        handleStop();
        database.DatabaseConnection.disconnect();
        
        Parent root = FXMLLoader.load(getClass().getResource("/views/admin_login.fxml"));
        
        stage.getScene().setRoot(root);
        stage.setFullScreen(false);
    }
    
    private void handleStop() {
        try {
            if (server != null) {
                try {
                    java.rmi.server.UnicastRemoteObject.unexportObject(server, true);
                } catch (java.rmi.NoSuchObjectException e) {
                }
                server = null; 
            }

            if (registry != null) {
                try {
                    java.rmi.server.UnicastRemoteObject.unexportObject(registry, true);
                } catch (java.rmi.NoSuchObjectException e) {
                }
                registry = null;
            }
            
            if (con != null && !con.isClosed()) {
                try (PreparedStatement pst = con.prepareStatement("UPDATE internet_cafe.computer SET status = 'offline' WHERE computer_id > 0")) {
                    pst.executeUpdate();
                } catch (Exception e) {
                }
            }
            
            if (con != null && !con.isClosed()) {
                try {
                    con.close();
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
        }
    }

    @FXML
    private void toggleSidebar(ActionEvent event) {
    }

    @FXML
    private void goOverview(ActionEvent event) throws IOException { 
        switchScene("/views/admin_dashboard.fxml"); 
    }

    @FXML
    private void goCustomers(ActionEvent event) throws IOException { 
        switchScene("/views/customer.fxml"); 
    }

    @FXML
    private void goPCs(ActionEvent event) throws IOException { 
        switchScene("/views/pc_view.fxml"); 
    }

    @FXML
    private void goInventory(ActionEvent event) throws IOException { 
        switchScene("/views/inventory.fxml"); 
    }

    @FXML
    void goStaff(ActionEvent event) throws IOException { 
        switchScene("/views/staff.fxml"); 
    }

    @FXML
    private void onRefresh(ActionEvent event) {
        if (searchField != null) {
            searchField.clear();
        }
        refreshGames();
    }

    @FXML
    private void onAddGame(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/game_modal.fxml"));
            Parent root = loader.load();

            GameModalController controller = loader.getController();
            controller.setParentController(this);
            controller.initForAdd();

            if (controller instanceof InitializableController) {
                ((InitializableController) controller).setServer(server);
                ((InitializableController) controller).setClient(client);
                ((InitializableController) controller).setDatabaseConnection(con);
            }

            PixelMotion.showOverlayInStack(contentStack, root, false, this::refreshGames);
        } catch (IOException e) {
            showError("Modal Error", e.getMessage());
        }
    }

    @FXML
    private void handleSearchAction(KeyEvent event) {
        refreshGames();
    }

    private void showError(String title, String message) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(topSearchField, title,
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
    
    public StackPane getContentStack() {
        return contentStack;
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        if (con != null) {
            Platform.runLater(() -> loadGames(""));
        }
    }
}