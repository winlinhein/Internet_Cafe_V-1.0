/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/javafx/FXMLController.java to edit this template
 */
package admin_controllers;

import animation.PixelMotion;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * FXML Controller class
 *
 * @author Hello
 */
public class AddNewPointController implements Initializable {

    @FXML
    private VBox rootModal;
    @FXML
    private Label titleLabel;
    @FXML
    private TextField amountField;

    /**
     * Initializes the controller class.
     */
    private Customer selectedCustomer;
    private AddCustomerController cardController;
    
     public void setParentController(AddCustomerController controller) { 
        this.cardController = controller; 
    }
     
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        amountField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*(\\.\\d*)?")) {
                amountField.setText(oldValue);
            }
        });
    }    

    @FXML
    private void close(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    @FXML
    private void save(ActionEvent event) {
        String value = amountField.getText() == null ? "" : amountField.getText().trim();
        if(value.isEmpty())
            amountField.setPromptText("Please enter amount!");
        else{
            
            double addPoints = Double.parseDouble(value);
                 
            if (cardController != null) {
            
            cardController.updatePointsDisplay(addPoints);
        }   
            closeAddPointsPopup(event);
        }
    }
    
    @FXML 
    private void closeAddPointsPopup(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }
    
}
