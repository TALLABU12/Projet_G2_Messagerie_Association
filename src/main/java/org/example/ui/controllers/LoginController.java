package org.example.ui.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.client.GuiClient;
import org.example.protocole.Response;
import org.example.protocole.ResponseStatus;

public class LoginController {

    @FXML private TextField     nameField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;   // optional: disable during request

    private static final String HOST = "127.0.0.1";
    private static final int    PORT = 5000;

    @FXML
    private void handleLoginButton() {
        String username = nameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champs manquants",
                    "Veuillez renseigner votre nom d'utilisateur et votre mot de passe.");
            return;
        }

        // Disable button to prevent double-clicks while connecting
        if (loginButton != null) loginButton.setDisable(true);

        GuiClient client = new GuiClient(HOST, PORT);

        // Network call must happen off the FX thread
        Thread loginThread = new Thread(() -> {
            try {
                Response response = client.connectAndLogin(username, password);

                Platform.runLater(() -> {
                    if (response.getStatus() == ResponseStatus.SUCCESS) {
                        openHomeView(client);
                    } else {
                        if (loginButton != null) loginButton.setDisable(false);
                        showAlert(Alert.AlertType.ERROR, "Accès refusé", response.getMessage());
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (loginButton != null) loginButton.setDisable(false);
                    showAlert(Alert.AlertType.ERROR, "Erreur de connexion",
                            "Impossible de joindre le serveur : " + e.getMessage());
                });
            }
        }, "Login-Thread");

        loginThread.setDaemon(true);
        loginThread.start();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void openHomeView(GuiClient client) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/app/homeView.fxml"));
            Parent root = loader.load();

            // TODO: uncomment once HomeController exists
            // HomeController homeCtrl = loader.getController();
            // homeCtrl.init(client);

            Stage stage = (Stage) nameField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Messagerie – " + client.getUsername());
            stage.show();

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur",
                    "Impossible d'ouvrir la vue principale : " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}