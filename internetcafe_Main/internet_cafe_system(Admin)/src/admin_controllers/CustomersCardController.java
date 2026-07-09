package admin_controllers;

import animation.PixelMotion;
import member_controllers.ClientInterface;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CustomersCardController implements Initializable, InitializableController {

    @FXML private StackPane cardShell;
    @FXML private VBox cardRoot;
    @FXML private StackPane avatarWrap;
    @FXML private Label initialsLabel;
    @FXML private Label nameLabel;
    @FXML private Label metaLabel;
    @FXML private VBox pointsStat;
    @FXML private Label pointsLabel;
    @FXML private VBox spentStat;
    @FXML private Label spentLabel;
    @FXML private Label lastVisitLabel;

    private Customer currentCustomer;
    private CustomersController mainController;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private int currentMemberId;
    private int lastPoints = -1;
    private boolean isLoadingTotalSpent = false;
    private int lastFetchedTotal = -1;
    private EditCustomerController currentEditController;

    public void setParentController(CustomersController controller) {
        this.mainController = controller;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (cardShell != null) PixelMotion.applyUltraCardHover(cardShell);
    }

    @FXML
    private void handleCardClick(MouseEvent event) {
        openModal(event);
    }

    public void setData(Customer customer) {
        this.currentCustomer = customer;
        this.currentMemberId = customer.getMemberId();
    
        initialsLabel.setText(customer.getMemberName());
        nameLabel.setText(customer.getMemberName());
        metaLabel.setText("MEM-" + customer.getMemberId());
        pointsLabel.setText(customer.getPoint() + " pts");
    

        spentLabel.setText("... pts");
    

        refreshTotalSpent();
    }
    
    public void setCurrentEditController(EditCustomerController controller) {
        this.currentEditController = controller;
    }
    
    public void updateTier(String newTier) {
        Platform.runLater(() -> {
            if (currentEditController != null) {
                currentEditController.updateTierDisplay(newTier);
            }
        });
    }

    @FXML
    private void openModal(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/edit_customer.fxml"));
            Parent root = loader.load();

            EditCustomerController editController = loader.getController();
            editController.setParentController(this);
            editController.initData(currentCustomer);
            this.currentEditController = editController;

            if (editController instanceof InitializableController) {
                ((InitializableController) editController).setServer(server);
                ((InitializableController) editController).setClient(client);
                ((InitializableController) editController).setDatabaseConnection(con);
                System.out.println("CustomersCardController: Passed connection to EditCustomerController: " + (con != null ? "Available" : "NULL"));
            }

            StackPane hostStack = mainController != null ? mainController.getContentStack() : null;
            if (hostStack != null) {
                PixelMotion.showOverlayInStack(hostStack, root, false);
            } else {
                PixelMotion.playWindowIntro(root, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
            showError("Error", "Failed to open edit modal: " + e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(nameLabel, title,
                message == null ? "Unknown error" : message,
                PixelMotion.ToastType.ERROR));
    }

    // ---------- RMI injection ----------
    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("CustomersCardController: Server injected");
    }

    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("CustomersCardController: Client injected");
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("CustomersCardController: Database connection injected: " + (con != null ? "Available" : "NULL"));
    }

    public int getMemberId() {
        return currentCustomer != null ? currentCustomer.getMemberId() : -1;
    }

    public String getMemberName() {
        return currentCustomer != null ? currentCustomer.getMemberName() : "";
    }

    // ---------- Point updates ----------
    public void updatePoints(int newPoints) {
        if (currentCustomer != null) {
            double current = currentCustomer.getPoint();
            if (newPoints > current) {
                System.out.println("Ignoring stale tick: current=" + current + ", new=" + newPoints);
                return;
            }
        }
        applyPoints(newPoints);
    }

    public void setPointsFromAdmin(int newPoints) {
        Platform.runLater(() -> {
            if (currentCustomer != null) {
                currentCustomer.setPoint(newPoints);
                pointsLabel.setText(newPoints + " pts");
                lastPoints = newPoints;
                System.out.println("Card " + currentCustomer.getMemberId() + " points set to " + newPoints + " by admin callback");
            }
        });
    }

    private void applyPoints(int newPoints) {
        if (newPoints == lastPoints) return;
        lastPoints = newPoints;
        Platform.runLater(() -> {
            if (currentCustomer != null) {
                currentCustomer.setPoint(newPoints);
                pointsLabel.setText(newPoints + " pts");
            }
        });
    }

    public void updateName(String newName) {
        if (currentCustomer != null) {
            currentCustomer.setMemberName(newName);
            nameLabel.setText(newName);
            initialsLabel.setText(newName);
        }
    }

    public void refreshTotalSpent() {
        if (currentMemberId <= 0) return;
        
        new Thread(() -> {
            int totalSpent = fetchTotalSpentFromDB(currentMemberId);
            isLoadingTotalSpent = false;
            
            // ✅ Only update if value actually changed
            if (totalSpent != lastFetchedTotal) {
                lastFetchedTotal = totalSpent;
                Platform.runLater(() -> {
                    spentLabel.setText(totalSpent + " pts");
                    spentLabel.requestLayout();
                    System.out.println("Refreshed total spent for member " + currentMemberId + ": " + totalSpent);
                });
            }
        }).start();
    }
    

     private int fetchTotalSpentFromDB(int memberId) {
        String sql = "SELECT COALESCE(SUM(session_total_cost), 0) AS total_spent " +
                     "FROM internet_cafe.session WHERE member_id = ?";
        
        try (Connection dbCon = new database.DatabaseConnection().connectDB();
             PreparedStatement pst = dbCon.prepareStatement(sql)) {
            pst.setInt(1, memberId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total_spent");
                    System.out.println("📊 DB Query: member " + memberId + " total spent = " + total);
                    return total;
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching total spent for member " + memberId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }
     

}