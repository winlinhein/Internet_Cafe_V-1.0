package admin_controllers;

import animation.PixelMotion;

import member_controllers.ClientInterface;
import javafx.event.ActionEvent;
import javafx.fxml.*;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.image.*;
import javafx.scene.layout.StackPane;
import javafx.stage.*;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class InventoryCardController implements InitializableController, Initializable {
    
    @FXML private StackPane cardShell;
    @FXML private javafx.scene.layout.VBox cardRoot;
    @FXML private ImageView productImage;
    @FXML private Label titleLabel, subLabel, priceLabel, stockLabel;

    private Product currentProduct;
    private InventoryController mainController;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;


    @Override
    public void initialize(java.net.URL url, java.util.ResourceBundle rb) {
        if (cardShell != null) {
            PixelMotion.applyUltraCardHover(cardShell);
        } else if (cardRoot != null) {
            PixelMotion.applyUltraCardHover(cardRoot);
        }
    }
    public void setParentController(InventoryController controller) {
        this.mainController = controller;
    }

    public void setData(Product product) {
        this.currentProduct = product;
        titleLabel.setText(product.getName());
        priceLabel.setText("Point: " + product.getPrice());
        subLabel.setText(product.getCategory());

        int stock = product.getStock();
        stockLabel.setText("Stock: " + stock);

        // Remove any previous stock state classes
        stockLabel.getStyleClass().removeAll("low", "critical");

        if (stock == 0) {
            stockLabel.setText("⚠ OUT");
            stockLabel.getStyleClass().add("critical");
        } else if (stock <= 3) {
            stockLabel.setText("⚠ " + stock + " left");
            stockLabel.getStyleClass().add("critical");
        } else if (stock <= 10) {
            stockLabel.setText("↓ Stock: " + stock);
            stockLabel.getStyleClass().add("low");
        }

        String path = System.getProperty("user.dir") + "/src/dbimages/" + product.getImageUrl();
        File file = new File(path);
        if (file.exists()) {
            productImage.setImage(new Image(file.toURI().toString(), 80, 80, true, true));
        }
    }

    @FXML
    private void edit(ActionEvent event) {
        openModal(event);
    }
    
    @FXML
    private void openModal(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/edit_inventory.fxml"));
            Parent root = loader.load();

            EditInventoryController editController = loader.getController();
            editController.setParentController(this);       
            editController.initData(currentProduct);
            editController.setRefreshCallback(() -> {
                if (mainController != null) {
                    try {
                        mainController.loadProducts("");
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            });
            
            // CRITICAL: Pass database connection and RMI references to edit modal
            if (editController instanceof InitializableController) {
                ((InitializableController) editController).setServer(server);
                ((InitializableController) editController).setClient(client);
                ((InitializableController) editController).setDatabaseConnection(con);
                System.out.println("InventoryCardController: Passed connection to EditInventoryController: " + (con != null ? "Available" : "NULL"));
            }
            
            StackPane hostStack = mainController != null ? mainController.getContentStack() : null;
            if (hostStack != null) {
                PixelMotion.showOverlayInStack(hostStack, root, false);
            } else {
                PixelMotion.playWindowIntro(root, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Implement InitializableController interface methods
    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("InventoryCardController: Server injected");
    }
    
    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("InventoryCardController: Client injected");
    }
    
    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("InventoryCardController: Database connection injected: " + (con != null ? "Available" : "NULL"));
    }
}