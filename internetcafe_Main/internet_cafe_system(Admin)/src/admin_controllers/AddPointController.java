package admin_controllers;

import animation.PixelMotion;
import member_controllers.ClientInterface;
import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class AddPointController implements Initializable, InitializableController {

    @FXML private VBox rootModal;
    @FXML private Label titleLabel;
    @FXML private Label currentLabel;
    @FXML private TextField amountField;

    private Customer selectedCustomer;
    private EditCustomerController parentController;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;

    public void setParentController(EditCustomerController controller) { 
        this.parentController = controller; 
    }

    public void initPoint(Customer customer) {
        this.selectedCustomer = customer;
        currentLabel.setText("Current " + customer.getPoint() + " pts");
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
    private void save(ActionEvent event) throws RemoteException {
        if (amountField.getText().isEmpty()) {
            amountField.setPromptText("Please enter amount!");
            return;
        }

        try {
            double addPoints = Double.parseDouble(amountField.getText());
            if (addPoints <= 0) {
                amountField.setPromptText("Enter positive amount");
                return;
            }

            double currentPoints = selectedCustomer.getPoint();
            double newPoints = currentPoints + addPoints;

            if (con == null) {
                showError("Database Error", "No database connection");
                return;
            }

            String sql = "UPDATE internet_cafe.member SET point = ? WHERE member_id = ?";
            try (PreparedStatement pst = con.prepareStatement(sql)) {
                pst.setDouble(1, newPoints);
                pst.setInt(2, selectedCustomer.getMemberId());
                pst.executeUpdate();
            }

            String pointSaleSql = "INSERT INTO internet_cafe.point_sale (member_id, date, amount) VALUES (?, ?, ?)";
            try (PreparedStatement pst = con.prepareStatement(pointSaleSql)) {
                pst.setInt(1, selectedCustomer.getMemberId());
                pst.setDate(2, Date.valueOf(LocalDate.now()));
                pst.setDouble(3, addPoints);
                pst.executeUpdate();
            }

            selectedCustomer.setPoint(newPoints);

            if (parentController != null) {
                parentController.updatePointsDisplay(newPoints);
            }

            if (server != null) {
                server.updateMemberPointsAndNotify(selectedCustomer.getMemberId(), (int) newPoints);

                if (server instanceof ServerImpl) {
                    ((ServerImpl) server).checkAndUpdateMemberTierAfterPointPurchase(
                        selectedCustomer.getMemberId(), addPoints);
                }
            }

            closeAddPointsPopup(event);
        } catch (NumberFormatException e) {
            amountField.setPromptText("Enter a valid number");
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to add points: " + e.getMessage());
        }
    }

    @FXML
    private void cancel(ActionEvent event) {
        close(event);
    }

    @FXML 
    private void closeAddPointsPopup(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    private void showError(String title, String message) {
        javafx.application.Platform.runLater(() ->
            animation.PixelMotion.toastGlitch(titleLabel, title,
                message == null ? "Unknown error" : message,
                animation.PixelMotion.ToastType.ERROR));
    }

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
}