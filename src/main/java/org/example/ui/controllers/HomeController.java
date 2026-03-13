package org.example.ui.controllers;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.client.GuiClient;
import org.example.entity.Message;
import org.example.entity.User;
import org.example.enums.Role;
import org.example.enums.UserStatus;
import org.example.protocole.Response;
import org.example.protocole.ResponseStatus;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HomeController implements Initializable {

    // ── FXML fields ───────────────────────────────────────────────────────────

    @FXML private Label              currentUserLabel;
    @FXML private Button             usersBtn;
    @FXML private Button             quitBtn;

    @FXML private Button             addConversationBtn;
    @FXML private ListView<String>   conversationListView;

    @FXML private BorderPane         chatPanel;
    @FXML private Label              destinataireNameLabel;
    @FXML private Label              destinataireRoleLabel;
    @FXML private ListView<HBox>     chatListView;
    @FXML private TextArea           messageTextArea;
    @FXML private Button             sendBtn;

    // ── Internal state ────────────────────────────────────────────────────────

    private GuiClient  client;
    private String     myUsername;
    private Role       myRole;

    private String     selectedRecipient = null;

    /** Cache of all known users — used for role badge and passed to UserController. */
    private final Map<String, User>          knownUsers   = new HashMap<>();
    private final Map<String, List<Message>> messageCache = new HashMap<>();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ── Catppuccin Mocha ──────────────────────────────────────────────────────
    private static final String BG_BASE    = "#1e1e2e";
    private static final String BG_MANTLE  = "#181825";
    private static final String BG_SURFACE = "#313244";
    private static final String BG_OVERLAY = "#45475a";
    private static final String FG_TEXT    = "#cdd6f4";
    private static final String FG_SUBTEXT = "#a6adc8";
    private static final String FG_MUTED   = "#585b70";
    private static final String BLUE       = "#89b4fa";
    private static final String MAUVE      = "#cba6f7";
    private static final String RED        = "#f38ba8";
    private static final String PEACH      = "#fab387";
    private static final String YELLOW     = "#f9e2af";

    // ── Initializable ─────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setChatPanelEnabled(false);

        conversationListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    Label avatar = new Label(item.substring(0, 1).toUpperCase());
                    avatar.setPrefSize(36, 36);
                    avatar.setMinSize(36, 36);
                    avatar.setAlignment(Pos.CENTER);
                    avatar.setStyle(
                            "-fx-background-color: " + BG_SURFACE + ";" +
                            "-fx-text-fill: " + BLUE + ";" +
                            "-fx-font-weight: bold;" +
                            "-fx-font-size: 15;" +
                            "-fx-background-radius: 18;");

                    Label name = new Label(item);
                    name.setStyle(
                            "-fx-text-fill: " + FG_TEXT + ";" +
                            "-fx-font-size: 14;" +
                            "-fx-font-weight: bold;");

                    HBox cell = new HBox(10, avatar, name);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    cell.setPadding(new Insets(6, 8, 6, 8));

                    setGraphic(cell);
                    setText(null);
                    boolean sel = item.equals(getListView().getSelectionModel().getSelectedItem());
                    setStyle(sel
                            ? "-fx-background-color: " + BG_SURFACE + "; -fx-background-radius: 8;"
                            : "-fx-background-color: transparent;");
                }
            }
        });

        conversationListView.getSelectionModel()
                            .selectedItemProperty()
                            .addListener((obs, oldVal, newVal) -> {
            conversationListView.refresh();
            if (newVal != null) onConversationSelected(newVal);
        });

        messageTextArea.addEventFilter(KeyEvent.KEY_PRESSED, ke -> {
            if (ke.getCode() == KeyCode.ENTER && !ke.isShiftDown()) {
                handleSend();
                ke.consume();
            }
        });
    }

    // ── Public init ──────────────────────────────────────────────────────────

    public void init(GuiClient guiClient, String username, Role role) {
        this.client     = guiClient;
        this.myUsername = username;
        this.myRole     = role;

        currentUserLabel.setText(username);
        client.setOnResponse(this::onServerResponse);
    }

    public void init(GuiClient guiClient, String username) {
        init(guiClient, username, Role.MEMBRE);
    }

    // ── Conversation selection ────────────────────────────────────────────────

    private void onConversationSelected(String recipient) {
        selectedRecipient = recipient;
        destinataireNameLabel.setText(recipient);

        User u = knownUsers.get(recipient);
        if (u != null) {
            destinataireRoleLabel.setText(roleBadgeText(u.getRole()));
            destinataireRoleLabel.setStyle(roleBadgeStyle(u.getRole()));
        } else {
            destinataireRoleLabel.setText("");
        }

        setChatPanelEnabled(true);
        renderCache(recipient);
        requestHistory(recipient);
    }

    private void setChatPanelEnabled(boolean enabled) {
        chatPanel.setDisable(!enabled);
        chatPanel.setStyle(enabled
                ? "-fx-background-color: " + BG_BASE + "; -fx-opacity: 1.0;"
                : "-fx-background-color: " + BG_BASE + "; -fx-opacity: 0.45;");
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void handleAddConversation() {
        showNewConversationDialog();
    }

    @FXML
    private void handleSend() {
        if (client == null || selectedRecipient == null) return;
        String content = messageTextArea.getText().trim();
        if (content.isEmpty()) return;
        try {
            client.sendMessage(selectedRecipient, content);
            appendBubble(selectedRecipient, myUsername, content, LocalDateTime.now(), true);
            messageTextArea.clear();
        } catch (Exception e) {
            showThemedAlert("Erreur", "Impossible d'envoyer le message : " + e.getMessage());
        }
    }

    /**
     * Navigate to userView.fxml.
     * Passes the current onResponse callback and the known-users cache to
     * UserController so it can restore things correctly on "← Retour".
     */
    @FXML
    private void handleUsersBtn() {
        if (client == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/app/userView.fxml"));
            Parent root = loader.load();

            UserController userCtrl = loader.getController();
            userCtrl.init(
                    client,
                    myUsername,
                    this::onServerResponse,          // restore on back
                    new ArrayList<>(knownUsers.values())
            );

            Stage stage = (Stage) usersBtn.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Messagerie – Utilisateurs");
        } catch (Exception e) {
            showThemedAlert("Erreur", "Impossible d'ouvrir la vue utilisateurs : " + e.getMessage());
        }
    }

    @FXML
    private void handleQuit() {
        if (client != null) client.logout();
        Platform.exit();
        System.exit(0);
    }

    // ── Server response handling ──────────────────────────────────────────────

    private void onServerResponse(Response response) {
        if (response.getStatus() != ResponseStatus.SUCCESS) return;
        Object data = response.getData();

        if (data instanceof List<?> list) {
            if (!list.isEmpty() && list.get(0) instanceof Message) {
                handleHistoryResponse(list);
            } else if (!list.isEmpty() && list.get(0) instanceof User) {
                handleAllUsersResponse(list);
            }
        } else if (data instanceof Message msg) {
            handleIncomingMessage(msg);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleHistoryResponse(List<?> rawList) {
        List<Message> messages = (List<Message>) rawList;
        if (messages.isEmpty()) return;
        Message first = messages.get(0);
        String partner = first.getSender().getUsername().equals(myUsername)
                ? first.getReceiver().getUsername()
                : first.getSender().getUsername();
        messageCache.put(partner, new ArrayList<>(messages));
        if (partner.equals(selectedRecipient)) renderCache(partner);
    }

    @SuppressWarnings("unchecked")
    private void handleAllUsersResponse(List<?> rawList) {
        List<User> users = (List<User>) rawList;

        for (User u : users) knownUsers.put(u.getUsername(), u);

        // Refresh role badge if a conversation is open
        if (selectedRecipient != null && knownUsers.containsKey(selectedRecipient)) {
            User u = knownUsers.get(selectedRecipient);
            destinataireRoleLabel.setText(roleBadgeText(u.getRole()));
            destinataireRoleLabel.setStyle(roleBadgeStyle(u.getRole()));
        }

        // For non-ORGANISATEUR: populate conversation list with known users
        if (myRole != Role.ORGANISATEUR) {
            for (User u : users) {
                String name = u.getUsername();
                if (!name.equals(myUsername) && !conversationListView.getItems().contains(name)) {
                    conversationListView.getItems().add(name);
                    messageCache.putIfAbsent(name, new ArrayList<>());
                }
            }
        }
    }

    private void handleIncomingMessage(Message msg) {
        String sender   = msg.getSender().getUsername();
        String receiver = msg.getReceiver().getUsername();
        String partner  = sender.equals(myUsername) ? receiver : sender;

        if (!conversationListView.getItems().contains(partner)) {
            conversationListView.getItems().add(partner);
            messageCache.putIfAbsent(partner, new ArrayList<>());
        }
        messageCache.computeIfAbsent(partner, k -> new ArrayList<>()).add(msg);

        if (partner.equals(selectedRecipient)) {
            appendBubble(partner, sender, msg.getContenu(), msg.getDateEnvoi(),
                         sender.equals(myUsername));
        }
    }

    // ── Bubble rendering ──────────────────────────────────────────────────────

    private void requestHistory(String recipient) {
        if (client == null) return;
        try { client.requestHistory(recipient); }
        catch (Exception e) { showThemedAlert("Erreur", "Impossible de charger l'historique."); }
    }

    private void renderCache(String recipient) {
        chatListView.getItems().clear();
        for (Message m : messageCache.getOrDefault(recipient, List.of())) {
            boolean isMine = m.getSender().getUsername().equals(myUsername);
            appendBubble(recipient, m.getSender().getUsername(),
                         m.getContenu(), m.getDateEnvoi(), isMine);
        }
    }

    private void appendBubble(String conversation, String senderUsername,
                               String content, LocalDateTime time, boolean isMine) {
        if (!conversation.equals(selectedRecipient)) return;

        HBox row = new HBox();
        row.setPadding(new Insets(4, 10, 4, 10));
        row.setMaxWidth(Double.MAX_VALUE);

        Label bubble = new Label(content);
        bubble.setWrapText(true);
        bubble.setMaxWidth(420);
        bubble.setPadding(new Insets(8, 12, 8, 12));

        String timeStr = (time != null) ? time.format(TIME_FMT) : "";
        Label ts = new Label(timeStr);
        ts.setStyle("-fx-text-fill: " + FG_MUTED + "; -fx-font-size: 10;");

        if (isMine) {
            bubble.setStyle(
                    "-fx-background-color: " + BLUE + ";" +
                    "-fx-text-fill: " + BG_BASE + ";" +
                    "-fx-background-radius: 14 14 2 14;" +
                    "-fx-font-size: 13;");
            row.setAlignment(Pos.CENTER_RIGHT);
            ts.setAlignment(Pos.BOTTOM_RIGHT);
            VBox vb = new VBox(2, bubble, ts);
            vb.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(vb);
        } else {
            bubble.setStyle(
                    "-fx-background-color: " + BG_SURFACE + ";" +
                    "-fx-text-fill: " + FG_TEXT + ";" +
                    "-fx-background-radius: 14 14 14 2;" +
                    "-fx-font-size: 13;");
            row.setAlignment(Pos.CENTER_LEFT);
            Label senderLbl = new Label(senderUsername);
            senderLbl.setStyle("-fx-text-fill: " + FG_SUBTEXT + "; -fx-font-size: 10; -fx-font-weight: bold;");
            VBox vb = new VBox(2, senderLbl, bubble, ts);
            vb.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(vb);
        }

        chatListView.getItems().add(row);
        chatListView.scrollTo(chatListView.getItems().size() - 1);
    }

    // ── "Nouvelle conversation" dialog ────────────────────────────────────────

    private void showNewConversationDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);

        VBox root = new VBox(14);
        root.setAlignment(Pos.TOP_LEFT);
        root.setPadding(new Insets(28, 32, 28, 32));
        root.setStyle(
                "-fx-background-color: " + BG_MANTLE + ";" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: " + BG_SURFACE + ";" +
                "-fx-border-radius: 12;" +
                "-fx-border-width: 1;");

        Label title    = new Label("Nouvelle conversation");
        title.setStyle("-fx-text-fill: " + FG_TEXT + "; -fx-font-size: 16; -fx-font-weight: bold;");

        Label subtitle = new Label("Entrez le nom d'utilisateur du destinataire");
        subtitle.setStyle("-fx-text-fill: " + FG_SUBTEXT + "; -fx-font-size: 12;");

        Label fieldLbl = new Label("Nom d'utilisateur");
        fieldLbl.setStyle("-fx-text-fill: " + FG_SUBTEXT + "; -fx-font-size: 11; -fx-font-weight: bold;");

        TextField input = new TextField();
        input.setPromptText("ex: nafi");
        input.setPrefWidth(300);
        input.setStyle(
                "-fx-control-inner-background: " + BG_SURFACE + ";" +
                "-fx-background-color: " + BG_SURFACE + ";" +
                "-fx-text-fill: " + FG_TEXT + ";" +
                "-fx-prompt-text-fill: " + FG_MUTED + ";" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: " + BG_OVERLAY + ";" +
                "-fx-border-radius: 8; -fx-border-width: 1;" +
                "-fx-padding: 9 12 9 12; -fx-font-size: 13;");

        Label errorLbl = new Label("");
        errorLbl.setStyle("-fx-text-fill: " + RED + "; -fx-font-size: 11;");
        errorLbl.setMinHeight(16);

        Button confirmBtn = styledButton("Ajouter",  BLUE,       BG_BASE);
        Button cancelBtn  = styledButton("Annuler",  BG_SURFACE, FG_SUBTEXT);
        HBox buttons = new HBox(10, cancelBtn, confirmBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, subtitle, fieldLbl, input, errorLbl, buttons);

        confirmBtn.setOnAction(e -> {
            String name = input.getText().trim();
            if (name.isEmpty()) { errorLbl.setText("Le nom ne peut pas être vide."); return; }
            if (name.equals(myUsername)) { errorLbl.setText("Vous ne pouvez pas vous écrire à vous-même."); return; }
            if (conversationListView.getItems().contains(name)) { errorLbl.setText("Cette conversation existe déjà."); return; }
            conversationListView.getItems().add(name);
            messageCache.putIfAbsent(name, new ArrayList<>());
            dialog.close();
        });

        cancelBtn.setOnAction(e -> dialog.close());
        input.setOnKeyPressed(ke -> { if (ke.getCode() == KeyCode.ENTER) confirmBtn.fire(); });
        root.setOnKeyPressed(ke -> { if (ke.getCode() == KeyCode.ESCAPE) dialog.close(); });

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ── Themed alert ──────────────────────────────────────────────────────────

    private void showThemedAlert(String title, String message) {
        Platform.runLater(() -> {
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UNDECORATED);

            VBox root = new VBox(14);
            root.setPadding(new Insets(24, 28, 24, 28));
            root.setStyle(
                    "-fx-background-color: " + BG_MANTLE + ";" +
                    "-fx-background-radius: 10;" +
                    "-fx-border-color: " + RED + ";" +
                    "-fx-border-radius: 10; -fx-border-width: 1;");

            Label lTitle = new Label(title);
            lTitle.setStyle("-fx-text-fill: " + RED + "; -fx-font-size: 14; -fx-font-weight: bold;");

            Label lMsg = new Label(message);
            lMsg.setWrapText(true);
            lMsg.setMaxWidth(300);
            lMsg.setStyle("-fx-text-fill: " + FG_TEXT + "; -fx-font-size: 12;");

            Button ok = styledButton("OK", RED, BG_BASE);
            ok.setOnAction(e -> dialog.close());
            HBox btnRow = new HBox(ok);
            btnRow.setAlignment(Pos.CENTER_RIGHT);

            root.getChildren().addAll(lTitle, lMsg, btnRow);
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            dialog.setScene(scene);
            dialog.showAndWait();
        });
    }

    // ── Role helpers ──────────────────────────────────────────────────────────

    private String roleBadgeText(Role role) {
        return switch (role) {
            case ORGANISATEUR -> "Organisateur";
            case BENEVOLE     -> "Benevole";     // no accent, intentional
            default           -> "Membre";
        };
    }

    private String roleBadgeStyle(Role role) {
        return "-fx-text-fill: " + roleColor(role) + ";" +
               "-fx-font-size: 11; -fx-font-weight: bold;" +
               "-fx-background-color: " + BG_SURFACE + ";" +
               "-fx-background-radius: 5; -fx-padding: 2 8 2 8;";
    }

    private String roleColor(Role role) {
        return switch (role) {
            case ORGANISATEUR -> MAUVE;
            case BENEVOLE     -> PEACH;
            default           -> YELLOW;
        };
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private Button styledButton(String text, String bgColor, String fgColor) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                "-fx-text-fill: " + fgColor + ";" +
                "-fx-font-weight: bold; -fx-font-size: 12;" +
                "-fx-background-radius: 7; -fx-cursor: hand;" +
                "-fx-padding: 8 18 8 18;");
        return btn;
    }
}
