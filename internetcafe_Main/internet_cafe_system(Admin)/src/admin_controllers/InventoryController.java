package admin_controllers;

import animation.PixelMotion;

import database.DatabaseConnection;
import static admin_controllers.Admin_Main.stage;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.input.KeyEvent;
import member_controllers.ClientInterface;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class InventoryController implements Initializable, InitializableController, InventoryCallback {

    @FXML private TextField inventorySearchField;
    @FXML private FlowPane productFlow;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private StackPane contentStack;

    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private Registry registry;
    private InventoryCallback stub;
    private boolean inventoryCallbackRegistered = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (con != null) {
            System.out.println("Database connection already available in InventoryController");
            Platform.runLater(() -> {
                try {
                    fetchCategories();
                    loadProducts("");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                registerWithServer();
            });
        } else {
            System.out.println("InventoryController: Waiting for database connection to be injected...");
        }
    }
    
    private void registerWithServer() {
        if (server == null || inventoryCallbackRegistered) return;
        try {
            stub = (InventoryCallback) UnicastRemoteObject.exportObject(this, 0);
            server.registerInventoryCallback(stub);
            inventoryCallbackRegistered = true;
            System.out.println("InventoryController registered for callbacks");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    
    public void unregisterCallback() {
        if (inventoryCallbackRegistered && server != null) {
            try {
                server.unregisterInventoryCallback(stub);
                UnicastRemoteObject.unexportObject(stub, true);
                inventoryCallbackRegistered = false;
                System.out.println("InventoryController unregistered from callbacks");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void onInventoryChanged() throws RemoteException {
        Platform.runLater(() -> {
            try {
                loadProducts(inventorySearchField.getText().trim());
                System.out.println("Inventory auto‑refreshed via callback");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
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
        if (prevCtrl instanceof InventoryController && prevCtrl != this) {
            ((InventoryController) prevCtrl).unregisterCallback();
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
                System.out.println("InventoryController: Linked PCViewController with ServerImpl");
            }
        }

        if (stage.getScene() == null) {
            stage.setScene(new Scene(rootNode));
        } else {
            stage.getScene().setRoot(rootNode);
        }
        stage.setFullScreen(true);

        this.unregisterCallback();
        Admin_Main.setActiveController(controller);
    }

    public void loadProducts(String query) throws SQLException {
        if (con == null) {
            System.err.println("Database connection is null, cannot load products");
            return;
        }

        new Thread(() -> {
            try {
                String selectedCategory = categoryComboBox.getValue();
                if (selectedCategory == null) selectedCategory = "All Products";

                String sql = !selectedCategory.equals("All Products") 
                    ? "SELECT * FROM internet_cafe.product WHERE product_name LIKE ? AND product_type = ?"
                    : "SELECT * FROM internet_cafe.product WHERE product_name LIKE ?";

                try (PreparedStatement pst = con.prepareStatement(sql)) {
                    pst.setString(1, "%" + query + "%");
                    if (!selectedCategory.equals("All Products")) pst.setString(2, selectedCategory);

                    try (ResultSet rs = pst.executeQuery()) {
                        URL cardUrl = getClass().getResource("/views/inventory_card.fxml");
                
                        java.util.List<StackPane> cardList = new java.util.ArrayList<>();

                        while (rs.next()) {
                            Product product = new Product(
                                rs.getInt("product_id"),
                                rs.getString("product_name"),
                                rs.getString("product_type"),
                                rs.getDouble("unit_price"),
                                rs.getInt("stock_qty"),
                                rs.getString("image")
                            );

                            FXMLLoader loader = new FXMLLoader(cardUrl);
                            StackPane card = loader.load();
                            InventoryCardController cardController = loader.getController();
                            cardController.setData(product);
                            cardController.setParentController(this);
                        
                            if (cardController instanceof InitializableController) {
                                ((InitializableController) cardController).setServer(server);
                                ((InitializableController) cardController).setClient(client);
                                ((InitializableController) cardController).setDatabaseConnection(con);
                            }
                    
                            cardList.add(card);
                        }

                        Platform.runLater(() -> {
                            productFlow.getChildren().setAll(cardList);
                            for (int i = 0; i < cardList.size(); i++) {
                                PixelMotion.playInventoryCardSpawn(cardList.get(i), i);
                            }
                            System.out.println("Loaded " + cardList.size() + " products");
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showError("Load Error", e.getMessage()));
            }
        }).start();
    }

    private void loadCardUI(URL url, Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(url);
            StackPane card = loader.load();

            InventoryCardController cardController = loader.getController();
            cardController.setData(product);
            cardController.setParentController(this);
            
            if (cardController instanceof InitializableController && con != null) {
                ((InitializableController) cardController).setDatabaseConnection(con);
            }

            productFlow.getChildren().add(card);
            PixelMotion.playInventoryCardSpawn(card, productFlow.getChildren().size() - 1);
        } catch (IOException ex) {
            System.err.println("Error loading card: " + ex.getMessage());
        }
    }

    private void fetchCategories() throws SQLException {
        if (con == null) {
            System.err.println("Cannot fetch categories: database connection is null");
            return;
        }
        
        Platform.runLater(() -> categoryComboBox.getItems().setAll("All Products"));
        
        String query = "SELECT DISTINCT product_type FROM internet_cafe.product";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) {
                String type = rs.getString("product_type");
                if (type != null) {
                    Platform.runLater(() -> categoryComboBox.getItems().add(type));
                }
            }
            Platform.runLater(() -> categoryComboBox.getSelectionModel().selectFirst());
        }
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
    private void goGames(ActionEvent event) throws IOException { 
        switchScene("/views/game.fxml"); 
    }
    
    @FXML 
    private void goStaff(ActionEvent event) throws IOException {
        switchScene("/views/staff.fxml");
    }
    
    @FXML
    public void onRefresh() throws SQLException {
        if (inventorySearchField != null) {
            inventorySearchField.clear();
        }
        loadProducts(""); 
    }

    @FXML
    private void handleSearchAction(KeyEvent event) throws SQLException {
        if (inventorySearchField != null && con != null) {
            loadProducts(inventorySearchField.getText().trim());
        }
    }
    
    @FXML 
    private void handleChooseAction(ActionEvent event) throws SQLException { 
        if (inventorySearchField != null && con != null) {
            loadProducts(inventorySearchField.getText().trim()); 
        }
    }

    @FXML
    private void onAddProduct(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/add_inventory.fxml"));
            Parent root = loader.load();

            AddInventoryController addController = loader.getController();
            addController.setParentController(this);       
            
            if (addController instanceof InitializableController) {
                ((InitializableController) addController).setServer(server);
                ((InitializableController) addController).setClient(client);
                ((InitializableController) addController).setDatabaseConnection(con);
            }

            addController.setRefreshCallback(() -> { 
                Thread thread = new Thread(() -> {
                    try {
                        loadProducts(""); 
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                });
                thread.setDaemon(true);
                thread.start();
            });
            
            PixelMotion.showOverlayInStack(contentStack, root, false);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Modal Error", e.getMessage());
        }
    }

    @FXML
    private void onRefreshBtnClick(ActionEvent event) {
        try {
            onRefresh(); 
        } catch (SQLException e) {
            System.err.println("Refresh failed: " + e.getMessage());
            showError("Refresh Error", e.getMessage());
        }
    }

    @FXML
    private void toggleSidebar(ActionEvent event) {
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
        unregisterCallback();
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
                    System.out.println("Server unexported from InventoryController");
                } catch (java.rmi.NoSuchObjectException e) {
                    System.out.println("Server was already unexported.");
                }
                server = null; 
            }

            if (registry != null) {
                try {
                    java.rmi.server.UnicastRemoteObject.unexportObject(registry, true);
                    System.out.println("Registry unexported from InventoryController");
                } catch (java.rmi.NoSuchObjectException e) {
                    System.out.println("Registry was already unexported.");
                }
                registry = null;
            }
            
            if (con != null && !con.isClosed()) {
                try (PreparedStatement pst = con.prepareStatement("UPDATE internet_cafe.computer SET status = 'offline' WHERE computer_id > 0")) {
                    int updated = pst.executeUpdate();
                    System.out.println("Updated " + updated + " PCs to offline status");
                } catch (Exception e) {
                    System.err.println("Error updating PC status: " + e.getMessage());
                }
            }
            
            if (con != null && !con.isClosed()) {
                try {
                    con.close();
                    System.out.println("Database connection closed from InventoryController");
                } catch (Exception e) {
                    System.err.println("Error closing database connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void showError(String title, String message) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(inventorySearchField, title,
                message == null ? "Unknown error" : message,
                PixelMotion.ToastType.ERROR));
    }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("InventoryController: Server injected");
        tryRegister();
    }

    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("InventoryController: Client injected");
    }
    
    public StackPane getContentStack() {
        return contentStack;
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("InventoryController: Database connection injected");
        if (con != null) {
            Platform.runLater(() -> {
                try {
                    fetchCategories();
                    loadProducts("");
                } catch (SQLException e) {
                    e.printStackTrace();
                    showError("Database Error", e.getMessage());
                }
            });
        }
        tryRegister();
    }
    
    private void tryRegister() {
        if (server != null && con != null && stub == null) {
            registerWithServer();
        }
    }
    
    public void refreshInventoryTable() {
        Thread thread = new Thread(() -> {
            try {
                loadProducts(inventorySearchField.getText().trim());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}