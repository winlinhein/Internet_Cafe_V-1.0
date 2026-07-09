package admin_controllers;

import animation.PixelMotion;
import member_controllers.ClientInterface;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.sql.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.scene.layout.VBox;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

public class EditInventoryController implements Initializable, InitializableController {

    @FXML private TextField editNameField, editPriceField, editStockField, editImagePathField;
    @FXML private ComboBox<String> comboType;
    @FXML private ImageView editImagePreview;
    @FXML private Label editImagePlaceholder;
    @FXML private VBox editPopup;

    private Product selectedProduct;
    private InventoryCardController cardController;
    private String savedImageName;
    private int product_id;
    private Runnable refreshCallback;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private String pendingCategory = null;
    private final String IMAGE_PATH = System.getProperty("user.dir") + File.separator + "src" + File.separator + "dbimages" + File.separator;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        System.out.println("EditInventoryController: Initialized, waiting for database connection...");
    }

    public void setParentController(InventoryCardController controller) {
        this.cardController = controller;
    }

    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }

    private void loadCategories() {
        if (con == null) {
            System.err.println("Cannot load categories: database connection is null");
            return;
        }
        
        List<String> categories = new ArrayList<>();
        String sql = "SELECT DISTINCT product_type FROM internet_cafe.product ORDER BY product_type";
        
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            
            while (rs.next()) {
                String category = rs.getString("product_type");
                if (category != null && !category.trim().isEmpty()) {
                    categories.add(category);
                }
            }
            
            System.out.println("Loaded " + categories.size() + " categories");
            
            Platform.runLater(() -> {
                String currentValue = comboType.getValue();
                if (pendingCategory != null) {
                    currentValue = pendingCategory;
                    pendingCategory = null;
                }
                
                comboType.getItems().clear();
                comboType.getItems().addAll(categories);
                
                if (currentValue != null && !currentValue.isEmpty()) {
                    if (!comboType.getItems().contains(currentValue)) {
                        comboType.getItems().add(currentValue);
                    }
                    comboType.setValue(currentValue);
                }
            });
            
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Failed to load categories: " + e.getMessage());
        }
    }

    public void initData(Product product) {
        this.selectedProduct = product;
        this.product_id = product.getId();

        editNameField.setText(product.getName());
        editPriceField.setText(String.valueOf(product.getPrice()));
        editStockField.setText(String.valueOf(product.getStock()));
        editStockField.setEditable(false);
        this.savedImageName = product.getImageUrl();

        String category = product.getCategory();
        if (category != null && !category.trim().isEmpty()) {
            if (comboType.getItems().isEmpty()) {
                pendingCategory = category;
            } else {
                if (!comboType.getItems().contains(category)) {
                    comboType.getItems().add(category);
                }
                comboType.setValue(category);
            }
        }

        if (savedImageName != null && !savedImageName.isEmpty()) {
            File f = new File(IMAGE_PATH + savedImageName);
            if (f.exists()) {
                editImagePreview.setImage(new Image(f.toURI().toString()));
                editImagePathField.setText(savedImageName);
                showImage(true);
            }
        }
    }

    @FXML
    private void saveEditedProduct(ActionEvent event) {
        if (con == null) {
            showAlert("Database Error", "Database connection not available");
            return;
        }

        if (comboType.getValue() == null || comboType.getValue().trim().isEmpty()) {
            showAlert("Input Error", "Please select a category.");
            return;
        }
        
        if (editNameField.getText().trim().isEmpty()) {
            showAlert("Input Error", "Please enter a product name.");
            return;
        }

        String sql = "UPDATE internet_cafe.product SET product_name=?, product_type=?, unit_price=?, image=? WHERE product_id=?";

        try (PreparedStatement pstmt = con.prepareStatement(sql)) {

            String newName = editNameField.getText().trim();
            String newCategory = comboType.getValue().trim();
            double newPrice = Double.parseDouble(editPriceField.getText().trim());

            if (newPrice < 0) {
                showAlert("Input Error", "Price cannot be negative.");
                return;
            }

            pstmt.setString(1, newName);
            pstmt.setString(2, newCategory);
            pstmt.setDouble(3, newPrice);
            pstmt.setString(4, savedImageName);
            pstmt.setInt(5, product_id);

            if (pstmt.executeUpdate() > 0) {
                selectedProduct.setName(newName);
                selectedProduct.setCategory(newCategory);
                selectedProduct.setPrice((int) newPrice);
                selectedProduct.setImageUrl(savedImageName);

                if (cardController != null) cardController.setData(selectedProduct);
                if (refreshCallback != null) refreshCallback.run();

                if (server != null) {
                    try {
                        server.notifyInventoryChanged();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                closeEditPopup(event);
            }
        } catch (NumberFormatException e) {
            showAlert("Input Error", "Please enter a valid number for price.");
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Database Error", "Could not update product in the database.\n" + e.getMessage());
        }
    }

    @FXML
    private void chooseImage(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = fc.showOpenDialog(((Node) event.getSource()).getScene().getWindow());

        if (file != null) {
            try {
                File dir = new File(IMAGE_PATH);
                if (!dir.exists()) dir.mkdirs();

                savedImageName = System.currentTimeMillis() + "_" + file.getName();
                Path destPath = Paths.get(IMAGE_PATH + savedImageName);

                Files.copy(file.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);

                editImagePathField.setText(savedImageName);
                editImagePreview.setImage(new Image(file.toURI().toString()));
                showImage(true);
            } catch (IOException e) {
                e.printStackTrace();
                showAlert("Image Error", "Could not save image: " + e.getMessage());
            }
        }
    }

    @FXML
    private void resetImage(ActionEvent event) {
        showImage(false);
        editImagePathField.clear();
        savedImageName = null;
    }

    private void showImage(boolean visible) {
        editImagePreview.setVisible(visible);
        editImagePlaceholder.setVisible(!visible);
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(editNameField, title,
                content == null ? "Unknown error" : content,
                PixelMotion.ToastType.ERROR));
    }

    @FXML
    private void closeEditPopup(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("EditInventoryController: Server injected");
    }

    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("EditInventoryController: Client injected");
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("EditInventoryController: Database connection injected: " + (con != null ? "Available" : "NULL"));
        
        if (con != null) {
            loadCategories();
        }
    }

    @FXML
    void addProduct(ActionEvent event) {
        if (selectedProduct == null) {
            showAlert("Error", "No product selected.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/addInventory.fxml"));
            Parent root = loader.load();

            AddInventory2Controller controller = loader.getController();
            controller.setParentController(null);
            controller.initProduct(selectedProduct);

            if (controller instanceof InitializableController) {
                ((InitializableController) controller).setServer(server);
                ((InitializableController) controller).setClient(client);
                ((InitializableController) controller).setDatabaseConnection(con);
            }

            controller.setRefreshCallback(() -> refreshProductData());

            PixelMotion.showOverlayInStack((StackPane) editPopup.getParent().getParent(), root, false);
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Modal Error", "Could not open stock adjustment.");
        }
    }

    private void refreshProductData() {
        if (selectedProduct == null || con == null) return;
        String sql = "SELECT stock_qty FROM internet_cafe.product WHERE product_id = ?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setInt(1, selectedProduct.getId());
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                selectedProduct.setStock(rs.getInt("stock_qty"));
                editStockField.setText(String.valueOf(selectedProduct.getStock()));
                if (cardController != null) {
                    cardController.setData(selectedProduct);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}