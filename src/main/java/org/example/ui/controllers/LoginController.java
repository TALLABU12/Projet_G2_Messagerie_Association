package org.example.ui.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.client.GuiClient;
import org.example.protocole.Response;
import org.example.protocole.ResponseStatus;
import org.example.ui.controllers.HomeController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LoginController {

    @FXML private TextField     nameField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         statusLabel;   // inline error/info feedback

    // Default fall back if login.config isn't found
    private String host = "127.0.0.1";
    private int    port = 5000;

    // init of the fxml lifecycle

    @FXML
    private void initialize() {
        loadConfig();
    }

    // Loing

    @FXML
    private void handleLoginButton() {
        String username = nameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Veuillez renseigner tous les champs.", true);
            return;
        }

        loginButton.setDisable(true);
        setStatus("Connexion en cours…", false);

        GuiClient client = new GuiClient(host, port);

        Thread loginThread = new Thread(() -> {
            try {
                Response response = client.connectAndLogin(username, password);

                Platform.runLater(() -> {
                    if (response.getStatus() == ResponseStatus.SUCCESS) {
                        openHomeView(client);
                    } else {
                        loginButton.setDisable(false);
                        setStatus("Accès refusé : " + response.getMessage(), true);
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    setStatus("Impossible de joindre le serveur (" + host + ":" + port + ")", true);
                });
            }
        }, "Login-Thread");

        loginThread.setDaemon(true);
        loginThread.start();
    }

    // if client login found

    /**
     * Looks for client.config.json in this order:
     *  1. Next to the JAR / working directory  →  ./client.config.json
     *  2. On the classpath                     →  /client.config.json 
     *
     * Falls back to defaults (127.0.0.1:5000) if neither is found or parsing fails as seen above.
     */
    private void loadConfig() {
        String json = null;

        // 1. External file next to JAR (preferred – no recompile needed)
        Path external = Paths.get("client.config.json");
        if (Files.exists(external)) {
            try {
                json = Files.readString(external, StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("[Config] Cannot read external client.config.json: " + e.getMessage());
            }
        }

        // 2. Fallback: bundled inside the JAR / resources
        if (json == null) {
            try (InputStream is = getClass().getResourceAsStream("/client.config.json")) {
                if (is != null) {
                    json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                System.err.println("[Config] Cannot read classpath client.config.json: " + e.getMessage());
            }
        }

        if (json != null) {
            parseJson(json);
        } else {
            System.out.println("[Config] No client.config.json found – using defaults ("
                    + host + ":" + port + ")");
        }
    }

    /**
     * Minimal JSON parser – no external library needed.
     * Reads "host" (string) and "port" (integer) fields.
     */
    private void parseJson(String json) {
        try {
            // Extract "host": "value"
            String hostVal = extractJsonString(json, "host");
            if (hostVal != null && !hostVal.isBlank()) {
                host = hostVal;
            }

            // Extract "port": number
            String portVal = extractJsonNumber(json, "port");
            if (portVal != null) {
                port = Integer.parseInt(portVal.trim());
            }

            System.out.println("[Config] Loaded server address: " + host + ":" + port);

        } catch (Exception e) {
            System.err.println("[Config] Failed to parse client.config.json, using defaults. Error: " + e.getMessage());
        }
    }

    private String extractJsonString(String json, String key) {
        // Matches  "key": "value"
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile(pattern).matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private String extractJsonNumber(String json, String key) {
        // Matches  "key": 1234
        String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile(pattern).matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // open home view ONLY IF AUTH SUCCEED

    private void openHomeView(GuiClient client) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/app/homeView.fxml"));
            Parent root = loader.load();

            HomeController homeCtrl = loader.getController();
            homeCtrl.init(client, client.getUsername());

            Stage stage = (Stage) nameField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Messagerie – " + client.getUsername());
            stage.setResizable(true);
            stage.show();

        } catch (Exception e) {
            setStatus("Impossible d'ouvrir la vue principale : " + e.getMessage(), true);
        }
    }

    // Helpers for text formatting

    private void setStatus(String message, boolean isError) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setStyle(isError
                    ? "-fx-text-fill: #f38ba8; -fx-font-size: 11;"   // red
                    : "-fx-text-fill: #a6e3a1; -fx-font-size: 11;"); // green
        }
    }
}
