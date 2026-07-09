/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/javafx/FXMLController.java to edit this template
 */
package admin_controllers;

import animation.PixelMotion;

import member_controllers.ClientInterface;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * FXML Controller class
 *
 * @author Hello
 */
public class AddPcModalController implements Initializable, InitializableController {

    @FXML
    private StackPane overlay;
    @FXML
    private VBox modalCard;
    @FXML
    private Label lblTitle;
    @FXML
    private Button btnClose;
    @FXML
    private Label lblStation;
    @FXML
    private TextField tfPcName;
    @FXML
    private Button btnAdd;

    private PC currentPC;
    private PCViewController mainController;
    private CustomersCardController cardController;
    private Runnable refreshCallback;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;

    public void setParentController(PCViewController controller) {
        this.mainController = controller;
    }
    
    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        PixelMotion.applyToLater(overlay);
        // Don't create connection here - use injected connection
        if (con != null) {
            System.out.println("AddPcModalController: Database connection available");
        } else {
            System.out.println("AddPcModalController: Waiting for database connection to be injected...");
        }
    }   
    
    @FXML
    void handleCloseAction(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }
    
    @FXML
    void handleAddAction(ActionEvent event) {
        if (tfPcName.getText().isEmpty()) {
            tfPcName.setPromptText("Enter PC Name");
            tfPcName.setStyle("-fx-prompt-text-fill: red;");
        } else {
            if (con == null) {
                System.err.println("Cannot add PC: database connection is null");
                showError("Database Error", "Database connection not available");
                return;
            }
            
            String sql = "INSERT INTO internet_cafe.computer(model, status) VALUES (?, ?)";
            try (PreparedStatement pst = con.prepareStatement(sql)) {
                pst.setString(1, tfPcName.getText());
                pst.setString(2, "offline");
             
                int rowsAffected = pst.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("PC added successfully: " + tfPcName.getText());
                    
                    if (refreshCallback != null) {
                        refreshCallback.run(); 
                    }
                    
                    closeEditPopup(event);
                } else {
                    showError("Error", "Failed to add PC. Please try again.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                showError("Database Error", "Could not add PC: " + e.getMessage());
            }
        }
    }
    
    @FXML 
    private void closeEditPopup(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }
    
    private void showError(String title, String message) {
        javafx.application.Platform.runLater(() ->
            animation.PixelMotion.toastGlitch(lblTitle, title,
                message == null ? "Unknown error" : message,
                animation.PixelMotion.ToastType.ERROR));
    }
    
    // Implement InitializableController interface methods
    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("AddPcModalController: Server injected");
    }
    
    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("AddPcModalController: Client injected");
    }
    
    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("AddPcModalController: Database connection injected");
    }
}