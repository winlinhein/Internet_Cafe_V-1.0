package admin_controllers;

import animation.PixelMotion;

import member_controllers.ClientInterface;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class AddInventoryController implements Initializable, InitializableController {
    
    @FXML private ComboBox<String> comboType;
    @FXML private ImageView editImagePreview;
    @FXML private Label editImagePlaceholder;
    @FXML private TextField editNameField, editPriceField, editStockField, editImagePathField;

    private String savedImageName = null; 
    private InventoryController parentController;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private Runnable refreshCallback;
    private final String IMAGE_DIR = System.getProperty("user.dir") + File.separator + "src" + File.separator + "dbimages" + File.separator;
    
    public void setParentController(InventoryController controller) { 
        this.parentController = controller; 
    }
    
    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }
     
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Don't create connection here - wait for injection
        System.out.println("AddInventoryController: Initialized, waiting for database connection...");
        
    }

    private void loadCategories() {
        if (con == null) {
            System.err.println("Cannot load categories: database connection is null");
            return;
        }
        
        String sql = "SELECT DISTINCT product_type FROM internet_cafe.product";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            
            Platform.runLater(() -> comboType.getItems().clear());
            while (rs.next()) {
                String category = rs.getString("product_type");
                if (category != null && !category.trim().isEmpty()) {
                    Platform.runLater(() -> comboType.getItems().add(category));
                }
            }
            System.out.println("Loaded " + comboType.getItems().size() + " categories");
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Failed to load categories: " + e.getMessage());
        }
    }

    @FXML
    private void saveEditedProduct(ActionEvent event) {
        if (con == null) {
            showAlert("Database Error", "Database connection not available");
            return;
        }
        
        // Validate inputs
        if (comboType.getValue() == null || comboType.getValue().trim().isEmpty()) {
            showAlert("Input Error", "Please select a category.");
            return;
        }
        
        if (editNameField.getText().trim().isEmpty()) {
            showAlert("Input Error", "Please enter a product name.");
            return;
        }

        String sql = "INSERT INTO internet_cafe.product (product_type, product_name, unit_price, stock_qty, image) VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement pt = con.prepareStatement(sql)) {
            
            pt.setString(1, comboType.getValue().trim());
            pt.setString(2, editNameField.getText().trim());
            
            double price = 0.0;
            int stock = 0;
            
            try {
                price = editPriceField.getText().isEmpty() ? 0.0 : Double.parseDouble(editPriceField.getText().trim());
                stock = editStockField.getText().isEmpty() ? 0 : Integer.parseInt(editStockField.getText().trim());
            } catch (NumberFormatException e) {
                showAlert("Input Error", "Please enter valid numbers for price and stock.");
                return;
            }
            
            pt.setDouble(3, price);
            pt.setInt(4, stock);
            pt.setString(5, savedImageName); 
            
            int rowsAffected = pt.executeUpdate();
            if (rowsAffected > 0) {
            System.out.println("Product added successfully");
    
            if (refreshCallback != null) {
                refreshCallback.run(); 
            }

            if (server != null) {
                try {
                    server.notifyInventoryChanged();
                } catch (Exception e) {
                    e.printStackTrace();
            }
        }
    
            closeEditPopup(event);
    
            } else {
                showAlert("Database Error", "Failed to add product. No rows affected.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Could not add product: " + e.getMessage());
        }
    }

    @FXML
    private void chooseImage(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = fc.showOpenDialog(((Node)event.getSource()).getScene().getWindow());
        
        if (file != null) {
            try {
                // Create directory if it doesn't exist
                File dir = new File(IMAGE_DIR);
                if (!dir.exists()) {
                    if (dir.mkdirs()) {
                        System.out.println("Created image directory: " + IMAGE_DIR);
                    } else {
                        System.err.println("Failed to create image directory: " + IMAGE_DIR);
                    }
                }

                // Generate unique filename
                savedImageName = System.currentTimeMillis() + "_" + file.getName();
                Path destPath = Paths.get(IMAGE_DIR + savedImageName);
                
                // Copy file
                Files.copy(file.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Image saved: " + destPath.toString());
                
                // Update UI
                editImagePathField.setText(file.getAbsolutePath());
                Image image = new Image(file.toURI().toString(), 200, 200, true, true);
                editImagePreview.setImage(image);
                
                editImagePreview.setVisible(true);
                editImagePlaceholder.setVisible(false);
                
            } catch (IOException e) {
                e.printStackTrace();
                showAlert("File Error", "Could not save the image: " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Error processing image: " + e.getMessage());
            }
        }
    }

    @FXML 
    private void resetImage(ActionEvent event) {
        editImagePreview.setImage(null);
        editImagePreview.setVisible(false);
        editImagePlaceholder.setVisible(true);
        editImagePathField.clear();
        savedImageName = null;
        System.out.println("Image reset");
    }

    @FXML 
    private void closeEditPopup(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(editNameField, title,
                content == null ? "Unknown error" : content,
                PixelMotion.ToastType.WARN));
    }
    
    // Implement InitializableController interface methods
    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("AddInventoryController: Server injected");
    }
    
    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("AddInventoryController: Client injected");
    }
    
    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("AddInventoryController: Database connection injected: " + (con != null ? "Available" : "NULL"));
        
        // Load categories now that we have the connection
        if (con != null) {
            loadCategories();
        }
    }
}