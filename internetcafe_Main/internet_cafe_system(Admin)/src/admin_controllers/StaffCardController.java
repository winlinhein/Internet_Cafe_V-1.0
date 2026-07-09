package admin_controllers;

import animation.PixelMotion;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class StaffCardController implements Initializable {

    @FXML
    private javafx.scene.layout.VBox rootCard;
    @FXML
    private Label lblInitials;
    @FXML
    private Label lblName;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblRole;
    @FXML
    private Label lblEmail;
    @FXML
    private Label lblPhone;

    private Staff currentStaff;
    private StaffController mainController;

    public void setParentController(StaffController controller) {
        this.mainController = controller;
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        installSmallHoverAnimation();
    }    

    @FXML
    private void handleManageAction(ActionEvent event) {
        openModal(event);
    }
    
    public void setData(Staff staff) {
        this.currentStaff = staff;
        lblName.setText(staff.getAdmin_name());
        lblInitials.setText(staff.getInitials());
        lblStatus.setText(staff.getStatusLabel());
        lblRole.setText(staff.getRoleLabel());
        lblEmail.setText(staff.getEmail() == null || staff.getEmail().isBlank() ? "Not provided" : staff.getEmail());
        lblPhone.setText(staff.getPhone() == null || staff.getPhone().isBlank() ? "Not provided" : staff.getPhone());
    }
    
    @FXML
    private void openModal(ActionEvent event) {
        try {
            if (currentStaff == null) {
                PixelMotion.toastGlitch(rootCard, "Profile Error", "Staff data is missing.", PixelMotion.ToastType.ERROR);
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/profile.fxml"));
            Parent root = loader.load();

            ProfileController editController = loader.getController();
            editController.setParentController(this);       
            editController.initData(currentStaff);
            
            StackPane hostStack = mainController != null ? mainController.getContentStack() : null;
            if (hostStack != null && hostStack.getScene() != null) {
                PixelMotion.showOverlayInStack(hostStack, root, false);
            } else {
                Stage fallbackStage = new Stage();
                fallbackStage.setTitle("View Profile");
                fallbackStage.setScene(new javafx.scene.Scene(root));
                fallbackStage.initOwner(((Node) event.getSource()).getScene().getWindow());
                fallbackStage.show();
                PixelMotion.playWindowIntro(root, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            PixelMotion.toastGlitch(rootCard, "Profile Error", e.getMessage(), PixelMotion.ToastType.ERROR);
        }
    }

    private void installSmallHoverAnimation() {
        if (rootCard == null) {
            return;
        }

        ScaleTransition hoverIn = new ScaleTransition(Duration.millis(140), rootCard);
        hoverIn.setToX(1.012);
        hoverIn.setToY(1.012);
        hoverIn.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition hoverOut = new ScaleTransition(Duration.millis(150), rootCard);
        hoverOut.setToX(1.0);
        hoverOut.setToY(1.0);
        hoverOut.setInterpolator(Interpolator.EASE_BOTH);

        rootCard.setOnMouseEntered(event -> {
            hoverOut.stop();
            hoverIn.playFromStart();
        });

        rootCard.setOnMouseExited(event -> {
            hoverIn.stop();
            hoverOut.playFromStart();
        });
    }
}
