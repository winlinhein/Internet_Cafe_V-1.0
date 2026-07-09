package admin_controllers;

import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Admin_Main extends Application {
    public static Stage stage;
    private static Object activeController;

    public static Object getActiveController() {
        return activeController;
    }

    public static void setActiveController(Object controller) {
        activeController = controller;
    }
    
    @Override
    public void start(Stage stage) throws Exception {

        Admin_Main.stage = stage; 

        Parent root = FXMLLoader.load(getClass().getResource("/views/admin_login.fxml"));
        Scene scene = new Scene(root);

        stage.setScene(scene);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(false); 

        stage.show();
    }

    public static void main(String[] args) {
        System.setProperty("glass.win.uiScale", "1.0"); 
        launch(args);
    }
}