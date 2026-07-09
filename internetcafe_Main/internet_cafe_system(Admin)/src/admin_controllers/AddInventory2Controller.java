package admin_controllers;

import animation.PixelMotion;
import member_controllers.ClientInterface;
import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class AddInventory2Controller implements Initializable, InitializableController {

    @FXML private VBox rootModal;
    @FXML private Label titleLabel;
    @FXML private Label currentLabel;
    @FXML private TextField amountField;

    private Product selectedProduct;
    private InventoryController parentController;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private Runnable refreshCallback;
        
    public void setParentController(InventoryController controller) { 
        this.parentController = controller; 
    }

    public void initProduct(Product product) {
        this.selectedProduct = product;
        currentLabel.setText("Current stock: " + product.getStock());
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Allow only integers (no decimals for stock)
        amountField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                amountField.setText(oldValue);
            }
        });
    }

    @FXML
    private void close(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

   @FXML
    private void save(ActionEvent event) throws RemoteException {
        if (amountField.getText().isEmpty()) {
            amountField.setPromptText("Please enter amount!");
            return;
        }

        try {
            int addStock = Integer.parseInt(amountField.getText());
            if (addStock <= 0) {
                amountField.setPromptText("Enter positive amount");
                return;
            }

            int currentStock = selectedProduct.getStock();
            int newStock = currentStock + addStock;

            if (con == null) {
                showError("Database Error", "No database connection");
                return;
            }
            String sql = "UPDATE internet_cafe.product SET stock_qty = ? WHERE product_id = ?";
            try (PreparedStatement pst = con.prepareStatement(sql)) {
                pst.setInt(1, newStock);
                pst.setInt(2, selectedProduct.getId());
                pst.executeUpdate();
            }

            selectedProduct.setStock(newStock);

            if (parentController != null) {
                parentController.refreshInventoryTable();
            }

            // Update the edit form
            if (refreshCallback != null) {
                Platform.runLater(refreshCallback);
            }

            if (server != null) {
                server.notifyInventoryChanged();
            }

            closeAddInventoryPopup(event);

        } catch (NumberFormatException e) {
            amountField.setPromptText("Enter a valid number");
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to add stock: " + e.getMessage());
        }
    }

    @FXML
    private void cancel(ActionEvent event) {
        close(event);
    }

    @FXML 
    private void closeAddInventoryPopup(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    private void showError(String title, String message) {
        javafx.application.Platform.runLater(() ->
            animation.PixelMotion.toastGlitch(titleLabel, title,
                message == null ? "Unknown error" : message,
                animation.PixelMotion.ToastType.ERROR));
    }

    // --- InitializableController methods ---
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

    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }
}