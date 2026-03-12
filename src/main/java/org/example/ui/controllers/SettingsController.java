package controllers;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import javafx.stage.Stage;

public class SettingsController {

    @FXML
    private void backHome(ActionEvent event) {

        Stage stage = (Stage)((Node)event.getSource())
                .getScene()
                .getWindow();

        SceneManager.switchScene(stage, "homeView.fxml");
    }
}