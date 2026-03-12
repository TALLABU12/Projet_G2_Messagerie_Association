package controllers;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private void handleLogin(ActionEvent event) {

        Stage stage = (Stage)((Node)event.getSource())
                .getScene()
                .getWindow();

        SceneManager.switchScene(stage, "homeView.fxml");
    }

    @FXML
    private void handleSignup(ActionEvent event) {

        Stage stage = (Stage)((Node)event.getSource())
                .getScene()
                .getWindow();

        SceneManager.switchScene(stage, "signup.fxml");
    }
}