package org.example.ui.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.client.GuiClient;
import org.example.protocole.Response; // ensure this import matches your class package

import java.io.IOException;
import org.example.protocol.Request;
import org.example.protocole.RequestType;

public class LoginController {

    @FXML private TextField nameField;      // give fx:id="nameField" in loginView.fxml
    @FXML private PasswordField passwordField; // fx:id="passwordField"
    @FXML private Button submitButton;      // fx:id="submitButton" and onAction="#handleLoginButton"

    // change if server is remote
    private final String SERVER_HOST = "127.0.0.1";
    private final int SERVER_PORT = 5000;

    //private GuiClient guiClient;

    @FXML
    private void handleLoginButton() {
        String username = nameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage("Veuillez remplir tous les champs.");
            return;
        }

        submitButton.setDisable(true);

        // Do network work off the UI thread
        new Thread(() -> {
            try {
                Request loginRequest = new Request(RequestType.LOGIN, username, password);
                Response loginResponse = (Response) in.readObject();

                if (resp.getStatus() != null && resp.getStatus().name().equals("SUCCESS")) {
                    // success -> switch to HomeView.fxml on FX thread
                    javafx.application.Platform.runLater(() -> {
                        try {
                            Stage stage = (Stage) submitButton.getScene().getWindow();
                            Parent home = FXMLLoader.load(getClass().getResource("/app/homeView.fxml"));
                            stage.getScene().setRoot(home);
                            // store guiClient in stage userdata so HomeViewController can retrieve it
                            stage.setUserData(guiClient);
                        } catch (IOException e) {
                            e.printStackTrace();
                            showMessage("Erreur lors du chargement de l'interface.");
                        }
                    });
                } else {
                    // login failed -> show message
                    String msg = (resp != null && resp.getMessage() != null) ? resp.getMessage() : "Authentification échouée.";
                    javafx.application.Platform.runLater(() -> {
                        showMessage(msg);
                        submitButton.setDisable(false);
                    });
                    try { guiClient.close(); } catch (Exception ignored) {}
                }

            } catch (Exception e) {
                String m = e.getMessage() == null ? e.toString() : e.getMessage();
                javafx.application.Platform.runLater(() -> {
                    showMessage("Impossible de se connecter au serveur:\n" + m);
                    submitButton.setDisable(false);
                });
            }
        }, "Login-Worker").start();
    }

    // shows a tiny modal dialog with OK button
    private void showMessage(String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.showAndWait();
    }
}