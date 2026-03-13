package org.example.ui.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.example.client.GuiClient;
import org.example.entity.Message;
import org.example.entity.User;
import org.example.protocole.Response;
import org.example.protocole.ResponseStatus;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller linked to homeView.fxml
 *
 * Main role:
 * - Manage the list of known users / conversations shown in the left ListView.
 * - When a conversation is selected:
 *      enable the chat area,
 *      update the recipient label,
 *      load message history using GuiClient.
 * - Display messages as chat bubbles:
 *      sent messages on the right,
 *      received messages on the left.
 * - The "Send" button calls GuiClient.sendMessage()
 *      then directly adds the message to the UI.
 * - Incoming messages from the server (through GuiClient listener)
 *      go through onServerResponse() and are added
 *      to the correct conversation.
 *
 * Controller setup:
 * - After a successful login (for example inside GuiClient),
 *   get the controller using FXMLLoader.getController()
 *   then call:
 *      controller.init(guiClient, loggedInUsername);
 */
public class HomeController implements Initializable {

    // Fxml fields injected from HomeView
    @FXML
    private Label currentUserLabel;
    @FXML
    private Button usersBtn;
    @FXML
    private Button quitBtn;

    @FXML
    private Button addConversationBtn;
    @FXML
    private ListView<String> conversationListView;   // items = recipient usernames

    @FXML
    private BorderPane chatPanel;
    @FXML
    private Label destinataireNameLabel;
    @FXML
    private ListView<HBox> chatListView;             // items = rendered bubble rows
    @FXML
    private TextArea messageTextArea;
    @FXML
    private Button sendBtn;

    // Internal state
    private GuiClient client;
    private String myUsername;

    /**
     * Currently selected conversation recipient. Null = none selected.
     */
    private String selectedRecipient = null;

    /**
     * In-memory message cache, keyed by recipient username. Filled on first
     * load from history; new messages appended live.
     */
    private final java.util.Map<String, List<Message>> messageCache
            = new java.util.HashMap<>();

    private static final DateTimeFormatter TIME_FMT
            = DateTimeFormatter.ofPattern("HH:mm");

    // ── Initializable ─────────────────────────────────────────────────────────
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Chat panel starts visually disabled (no conversation selected)
        setChatPanelEnabled(false);

        // Conversation list click → select conversation
        conversationListView.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        onConversationSelected(newVal);
                    }
                });

        // Send on Enter (no Shift)
        messageTextArea.addEventFilter(KeyEvent.KEY_PRESSED, ke -> {
            if (ke.getCode() == KeyCode.ENTER && !ke.isShiftDown()) {
                handleSend();
                ke.consume();
            }
        });
    }

    /**
     * Must be called once after the FXML is loaded and login succeeded.
     *
     * @param guiClient connected, authenticated GuiClient
     * @param username the logged-in user's username
     */
    public void init(GuiClient guiClient, String username) {
        this.client = guiClient;
        this.myUsername = username;

        currentUserLabel.setText(username);

        // Route all server responses through this controller
        client.setOnResponse(this::onServerResponse);
    }

    // Conversation Selection
    private void onConversationSelected(String recipient) {
        selectedRecipient = recipient;
        destinataireNameLabel.setText(recipient);
        setChatPanelEnabled(true);

        // Render cached messages if any, then refresh from server
        renderCache(recipient);
        requestHistory(recipient);
    }

    /**
     * Enable/disable + fade the entire right-hand chat panel.
     */
    private void setChatPanelEnabled(boolean enabled) {
        chatPanel.setDisable(!enabled);
        chatPanel.setStyle(enabled
                ? "-fx-background-color: #1e1e2e; -fx-opacity: 1.0;"
                : "-fx-background-color: #1e1e2e; -fx-opacity: 0.45;");
    }

    // FXML adding a new conversation
    @FXML
    private void handleAddConversation() {
        // Ask the user to type the recipient's username
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nouvelle conversation");
        dialog.setHeaderText("Ajouter un destinataire");
        dialog.setContentText("Nom d'utilisateur :");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            String trimmed = name.trim();
            if (!trimmed.isEmpty() && !conversationListView.getItems().contains(trimmed)) {
                conversationListView.getItems().add(trimmed);
                messageCache.putIfAbsent(trimmed, new ArrayList<>());
            }
        });
    }

    @FXML
    private void handleSend() {
        if (client == null || selectedRecipient == null) {
            return;
        }

        String content = messageTextArea.getText().trim();
        if (content.isEmpty()) {
            return;
        }

        try {
            client.sendMessage(selectedRecipient, content);

            // Build a synthetic Message for local display
            // (server echo may arrive later; we show it immediately)
            appendBubble(selectedRecipient, myUsername, content, java.time.LocalDateTime.now(), true);

            messageTextArea.clear();
        } catch (Exception e) {
            showAlert("Erreur", "Impossible d'envoyer le message : " + e.getMessage());
        }
    }

    @FXML
    private void handleUsersBtn() {
        if (client == null) {
            return;
        }
        try {
            client.requestAllUsers();
        } catch (Exception e) {
            showAlert("Erreur", "Impossible de récupérer la liste : " + e.getMessage());
        }
    }

    @FXML
    private void handleQuit() {
        if (client != null) {
            client.logout();
        }
        Platform.exit();
        System.exit(0);
    }

    // Server response handling
    /**
     * Called on the JavaFX thread for every Response pushed by the server.
     */
    private void onServerResponse(Response response) {
        if (response.getStatus() != ResponseStatus.SUCCESS) {
            // Could show a status bar message; ignore silently for now
            return;
        }

        Object data = response.getData();

        if (data instanceof List) {
            List<?> list = (List<?>) data;
            if (!list.isEmpty() && list.get(0) instanceof Message) {
                // History
                handleHistoryResponse(list);
            } else if (!list.isEmpty() && list.get(0) instanceof User) {
                // All-users
                handleAllUsersResponse(list);
            }
        } else if (data instanceof Message) {
            // New message
            handleIncomingMessage((Message) data);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleHistoryResponse(List<?> rawList) {
        List<Message> messages = (List<Message>) rawList;
        if (messages.isEmpty()) {
            return;
        }

        // Determine the conversation partner from the first message
        Message first = messages.get(0);
        String partner = first.getSender().getUsername().equals(myUsername)
                ? first.getReceiver().getUsername()
                : first.getSender().getUsername();

        // Replace cache entirely (server is authoritative)
        messageCache.put(partner, new ArrayList<>(messages));

        // Re-render if this is the active conversation
        if (partner.equals(selectedRecipient)) {
            renderCache(partner);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleAllUsersResponse(List<?> rawList) {
        List<User> users = (List<User>) rawList;
        // Add users to the conversation list if not already present
        for (User u : users) {
            String name = u.getUsername();
            if (!name.equals(myUsername) && !conversationListView.getItems().contains(name)) {
                conversationListView.getItems().add(name);
                messageCache.putIfAbsent(name, new ArrayList<>());
            }
        }
    }

    private void handleIncomingMessage(Message msg) {
        String sender = msg.getSender().getUsername();
        String receiver = msg.getReceiver().getUsername();

        // The partner is the "other" person in the exchange
        String partner = sender.equals(myUsername) ? receiver : sender;

        // Ensure conversation exists in the sidebar
        if (!conversationListView.getItems().contains(partner)) {
            conversationListView.getItems().add(partner);
            messageCache.putIfAbsent(partner, new ArrayList<>());
        }

        // Cache the message
        messageCache.computeIfAbsent(partner, k -> new ArrayList<>()).add(msg);

        // If this conversation is currently open, append the bubble
        if (partner.equals(selectedRecipient)) {
            boolean isMine = sender.equals(myUsername);
            appendBubble(partner, sender, msg.getContenu(), msg.getDateEnvoi(), isMine);
        }
    }

    // History rendering
    private void requestHistory(String recipient) {
        if (client == null) {
            return;
        }
        try {
            client.requestHistory(recipient);
        } catch (Exception e) {
            showAlert("Erreur", "Impossible de charger l'historique : " + e.getMessage());
        }
    }

    /**
     * Clear the chat list and re-render from the in-memory cache.
     */
    private void renderCache(String recipient) {
        chatListView.getItems().clear();
        List<Message> msgs = messageCache.getOrDefault(recipient, List.of());
        for (Message m : msgs) {
            boolean isMine = m.getSender().getUsername().equals(myUsername);
            appendBubble(recipient,
                    m.getSender().getUsername(),
                    m.getContenu(),
                    m.getDateEnvoi(),
                    isMine);
        }
    }

    /**
     * Build one bubble row and add it to the chatListView.
     *
     * Sent messages → right-aligned, blue bubble (#89b4fa). Received msgs →
     * left-aligned, white bubble (#cdd6f4).
     */
    private void appendBubble(String conversation,
            String senderUsername,
            String content,
            java.time.LocalDateTime time,
            boolean isMine) {

        // Only add to the view if this conversation is currently shown
        if (!conversation.equals(selectedRecipient)) {
            return;
        }

        HBox row = new HBox();
        row.setPadding(new Insets(4, 10, 4, 10));
        row.setMaxWidth(Double.MAX_VALUE);

        // Bubble label
        Label bubble = new Label(content);
        bubble.setWrapText(true);
        bubble.setMaxWidth(420);
        bubble.setPadding(new Insets(8, 12, 8, 12));

        String timeStr = (time != null) ? time.format(TIME_FMT) : "";

        if (isMine) {
            // Sent: right side, blue
            bubble.setStyle(
                    "-fx-background-color: #89b4fa;"
                    + "-fx-text-fill: #1e1e2e;"
                    + "-fx-background-radius: 14 14 2 14;"
                    + "-fx-font-size: 13;");
            row.setAlignment(Pos.CENTER_RIGHT);

            // Timestamp label
            Label ts = new Label(timeStr);
            ts.setStyle("-fx-text-fill: #585b70; -fx-font-size: 10;");
            ts.setAlignment(Pos.BOTTOM_RIGHT);

            VBox vb = new VBox(2, bubble, ts);
            vb.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(vb);

        } else {
            // Received: left side, light
            bubble.setStyle(
                    "-fx-background-color: #313244;"
                    + "-fx-text-fill: #cdd6f4;"
                    + "-fx-background-radius: 14 14 14 2;"
                    + "-fx-font-size: 13;");
            row.setAlignment(Pos.CENTER_LEFT);

            // Sender name (shown for received only)
            Label sender = new Label(senderUsername);
            sender.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 10; -fx-font-weight: bold;");

            Label ts = new Label(timeStr);
            ts.setStyle("-fx-text-fill: #585b70; -fx-font-size: 10;");

            VBox vb = new VBox(2, sender, bubble, ts);
            vb.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(vb);
        }

        chatListView.getItems().add(row);

        // Auto-scroll to the latest message
        chatListView.scrollTo(chatListView.getItems().size() - 1);
    }

    // Helpers
    private void showAlert(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }
}
