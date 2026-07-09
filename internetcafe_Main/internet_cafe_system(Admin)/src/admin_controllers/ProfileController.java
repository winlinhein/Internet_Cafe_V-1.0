/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/javafx/FXMLController.java to edit this template
 */
package admin_controllers;

import animation.PixelMotion;

import member_controllers.ClientInterface;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import java.sql.Connection;
import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * FXML Controller class
 *
 * @author Hello
 */
public class ProfileController implements Initializable, InitializableController {

    @FXML
    private TextField nameField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField phoneField;

    private Staff selectedStaff;
    private StaffCardController cardController;
    private Connection con;
    private Runnable refreshCallback;
    private ServerInterface server;
    private ClientInterface client;
    
    public void setParentController(StaffCardController controller) { 
        this.cardController = controller; 
    }
    
    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Use injected connection if available
        if (con != null) {
            System.out.println("ProfileController: Database connection available");
        } else {
            System.out.println("ProfileController: Waiting for database connection to be injected...");
        }
    }    

    @FXML
    private void handleCloseAction(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }
    
    public void initData(Staff staff) {
        this.selectedStaff = staff;
        nameField.setText(staff.admin_name);
        emailField.setText(staff.getEmail());
        phoneField.setText(staff.getPhone());
        
        // Make fields read-only since it's just for viewing
        nameField.setEditable(false);
        emailField.setEditable(false);
        phoneField.setEditable(false);
    }
    
    // Implement InitializableController interface methods
    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("ProfileController: Server injected");
    }
    
    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("ProfileController: Client injected");
    }
    
    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("ProfileController: Database connection injected");
    }
}