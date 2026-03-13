package org.example.ui.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.example.client.GuiClient;
import org.example.entity.User;
import org.example.enums.Role;
import org.example.enums.UserStatus;
import org.example.protocole.Response;
import org.example.protocole.ResponseStatus;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class UserController implements Initializable {

    // ── FXML fields ───────────────────────────────────────────────────────────

    @FXML private Label              onlineCountLabel;
    @FXML private Label              totalCountLabel;
    @FXML private ListView<User>     userListView;
    @FXML private Button             reloadBtn;
    @FXML private Button             backBtn;
    @FXML private Label              lastRefreshLabel;

    // ── Internal state ────────────────────────────────────────────────────────

    private GuiClient   client;
    private String      myUsername;

    /** The previous onResponse callback on GuiClient — restored when we go back. */
    private java.util.function.Consumer<Response> previousCallback;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── Catppuccin Mocha ──────────────────────────────────────────────────────
    private static final String BG_BASE    = "#1e1e2e";
    private static final String BG_SURFACE = "#313244";
    private static final String BG_OVERLAY = "#45475a";
    private static final String FG_TEXT    = "#cdd6f4";
    private static final String FG_SUBTEXT = "#a6adc8";
    private static final String FG_MUTED   = "#585b70";
    private static final String BLUE       = "#89b4fa";
    private static final String MAUVE      = "#cba6f7";
    private static final String RED        = "#f38ba8";
    private static final String GREEN      = "#a6e3a1";
    private static final String PEACH      = "#fab387";
    private static final String YELLOW     = "#f9e2af";

    // ── Initializable ─────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        userListView.setCellFactory(lv -> new UserCell());
    }

    // ── Public init ──────────────────────────────────────────────────────────

    /**
     * Called by HomeController right after loading this view.
     *
     * @param guiClient        the shared, connected client
     * @param username         logged-in user's username
     * @param prevCallback     the HomeController's onResponse — restored on back
     * @param initialUsers     users already cached in HomeController (shown immediately)
     */
    public void init(GuiClient guiClient,
                     String username,
                     java.util.function.Consumer<Response> prevCallback,
                     List<User> initialUsers) {
        this.client           = guiClient;
        this.myUsername       = username;
        this.previousCallback = prevCallback;

        // Take over the response callback — we need to intercept user-list responses
        client.setOnResponse(this::onServerResponse);

        // Show whatever the home controller had cached, then immediately refresh
        if (!initialUsers.isEmpty()) {
            populateList(initialUsers);
        }
        triggerReload();
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void handleReload() {
        reloadBtn.setDisable(true);
        reloadBtn.setText("Chargement…");
        triggerReload();
    }

    @FXML
    private void handleBack() {
        // Restore the HomeController callback before switching scene
        if (client != null && previousCallback != null) {
            client.setOnResponse(previousCallback);
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/app/homeView.fxml"));
            Parent root = loader.load();

            // Re-init HomeController with the same client
            HomeController homeCtrl = loader.getController();
            homeCtrl.init(client, myUsername);

            Stage stage = (Stage) backBtn.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Messagerie – " + myUsername);
        } catch (Exception e) {
            showError("Impossible de revenir à l'accueil : " + e.getMessage());
        }
    }

    // ── Server response ───────────────────────────────────────────────────────

    private void onServerResponse(Response response) {
        if (response.getStatus() != ResponseStatus.SUCCESS) return;

        Object data = response.getData();
        if (data instanceof List<?> list
                && !list.isEmpty()
                && list.get(0) instanceof User) {

            @SuppressWarnings("unchecked")
            List<User> users = (List<User>) list;
            Platform.runLater(() -> populateList(users));
        }
        // All other response types (messages, history) are ignored here —
        // they'll be handled by HomeController once we go back.
    }

    // ── List population ───────────────────────────────────────────────────────

    private void populateList(List<User> users) {
        // Sort: online first, then by username
        List<User> sorted = users.stream()
                .sorted(Comparator
                        .comparing((User u) -> u.getStatus() != UserStatus.ONLINE)
                        .thenComparing(u -> u.getUsername().toLowerCase()))
                .toList();

        userListView.getItems().setAll(sorted);

        long online = sorted.stream().filter(u -> u.getStatus() == UserStatus.ONLINE).count();
        onlineCountLabel.setText(online + " en ligne");
        totalCountLabel.setText(sorted.size() + " membre(s) au total");

        lastRefreshLabel.setText("Mis à jour à " + LocalTime.now().format(TIME_FMT));

        reloadBtn.setDisable(false);
        reloadBtn.setText("↻  Actualiser");
    }

    private void triggerReload() {
        try {
            client.requestAllUsers();
        } catch (Exception e) {
            showError("Impossible de charger la liste : " + e.getMessage());
            reloadBtn.setDisable(false);
            reloadBtn.setText("↻  Actualiser");
        }
    }

    // ── Error helper ──────────────────────────────────────────────────────────

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            alert.setTitle("Erreur");
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }

    // ── Custom cell ───────────────────────────────────────────────────────────

    /**
     * One row per user:
     *   [ avatar ]  [ username ]      [ role badge ]   [ ● status ]
     */
    private class UserCell extends ListCell<User> {

        @Override
        protected void updateItem(User user, boolean empty) {
            super.updateItem(user, empty);

            if (empty || user == null) {
                setGraphic(null);
                setText(null);
                setStyle("-fx-background-color: transparent;");
                return;
            }

            boolean online = user.getStatus() == UserStatus.ONLINE;
            boolean isMe   = user.getUsername().equals(myUsername);

            // ── Avatar circle ─────────────────────────────────────────────────
            Label avatar = new Label(user.getUsername().substring(0, 1).toUpperCase());
            avatar.setPrefSize(36, 36);
            avatar.setMinSize(36, 36);
            avatar.setAlignment(Pos.CENTER);
            avatar.setStyle(
                    "-fx-background-color: " + BG_OVERLAY + ";" +
                    "-fx-text-fill: " + BLUE + ";" +
                    "-fx-font-weight: bold;" +
                    "-fx-font-size: 14;" +
                    "-fx-background-radius: 18;");

            // ── Username (+ "(vous)" suffix for self) ─────────────────────────
            String displayName = isMe ? user.getUsername() + "  (vous)" : user.getUsername();
            Label nameLbl = new Label(displayName);
            nameLbl.setStyle(
                    "-fx-text-fill: " + (isMe ? FG_SUBTEXT : FG_TEXT) + ";" +
                    "-fx-font-size: 13;" +
                    (isMe ? "-fx-font-style: italic;" : "-fx-font-weight: bold;"));

            HBox nameBox = new HBox(10, avatar, nameLbl);
            nameBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(nameBox, Priority.ALWAYS);
            nameBox.setMaxWidth(Double.MAX_VALUE);

            // ── Role badge ────────────────────────────────────────────────────
            Label roleLbl = new Label(roleBadgeText(user.getRole()));
            roleLbl.setPrefWidth(120);
            roleLbl.setAlignment(Pos.CENTER);
            roleLbl.setStyle(
                    "-fx-text-fill: " + roleColor(user.getRole()) + ";" +
                    "-fx-font-size: 11;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-color: " + BG_SURFACE + ";" +
                    "-fx-background-radius: 5;" +
                    "-fx-padding: 3 8 3 8;");

            // ── Status dot ────────────────────────────────────────────────────
            Label statusLbl = new Label(online ? "● En ligne" : "● Hors ligne");
            statusLbl.setPrefWidth(90);
            statusLbl.setStyle(
                    (online ? "-fx-text-fill: " + GREEN : "-fx-text-fill: " + RED) +
                    "; -fx-font-size: 11; -fx-font-weight: bold;");

            // ── Row assembly ──────────────────────────────────────────────────
            HBox row = new HBox(0, nameBox, roleLbl, statusLbl);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 8, 8, 8));

            setGraphic(row);
            setText(null);
            setStyle("-fx-background-color: transparent; -fx-padding: 2 0 2 0;");
        }
    }

    // ── Role helpers ──────────────────────────────────────────────────────────

    /**
     * Human-readable role label — no accents on BENEVOLE as per spec.
     */
    private String roleBadgeText(Role role) {
        return switch (role) {
            case ORGANISATEUR -> "Organisateur";
            case BENEVOLE     -> "Benevole";    // no accent
            default           -> "Membre";
        };
    }

    private String roleColor(Role role) {
        return switch (role) {
            case ORGANISATEUR -> MAUVE;   // purple
            case BENEVOLE     -> PEACH;   // orange
            default           -> YELLOW;  // yellow
        };
    }
}
