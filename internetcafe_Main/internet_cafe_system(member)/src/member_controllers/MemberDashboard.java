package member_controllers;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.InputStream;
import javafx.application.Platform;

public class MemberDashboard extends Application {
    public static Stage stage;
    private static boolean isFullScreen = false;

    @Override
    public void start(Stage primaryStage) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fonts/PressStart2P-Regular.ttf")) { 
            if (is != null) { 
                Font.loadFont(is, 10); 
            } else { 
                System.err.println("Could not find font file."); 
            }
        }

        primaryStage.initStyle(StageStyle.UNDECORATED);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/member_view/test.fxml"));
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("Internet Cafe Dashboard");
        primaryStage.setScene(scene);
        
        // Make the stage full screen
        primaryStage.setFullScreen(true);
        primaryStage.setFullScreenExitHint("Press ESC to exit full screen");
        
        primaryStage.show();
        
        // Force full-screen after showing (fix for some systems)
        Platform.runLater(() -> {
            primaryStage.setFullScreen(true);
            isFullScreen = true;
        });

        stage = primaryStage;
        
        // Add listener to maintain full-screen when focus changes
        stage.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && !stage.isFullScreen() && isFullScreen) {
                Platform.runLater(() -> stage.setFullScreen(true));
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}