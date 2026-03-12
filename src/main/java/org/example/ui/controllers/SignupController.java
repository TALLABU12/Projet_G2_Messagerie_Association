package controllers;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import javafx.stage.Stage;

public class SignupController {

    @FXML
    private void handleBackToLogin(ActionEvent event) {

        Stage stage = (Stage)((Node)event.getSource())
                .getScene()
                .getWindow();

        SceneManager.switchScene(stage, "login.fxml");
    }

    @FXML
    private void handleSignup(ActionEvent event) {

        Stage stage = (Stage)((Node)event.getSource())
                .getScene()
                .getWindow();

        SceneManager.switchScene(stage, "login.fxml");
    }
}