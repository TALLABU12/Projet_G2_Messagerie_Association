package controllers;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import javafx.stage.Stage;

public class HomeViewController {

    @FXML
    private void openSettings(ActionEvent event) {

        Stage stage = (Stage)((Node)event.getSource())
                .getScene()
                .getWindow();

        SceneManager.switchScene(stage, "settings.fxml");
    }

    @FXML
    private void logout(ActionEvent event) {

        Stage stage = (Stage)((Node)event.getSource())
                .getScene()
                .getWindow();

        SceneManager.switchScene(stage, "login.fxml");
    }
}