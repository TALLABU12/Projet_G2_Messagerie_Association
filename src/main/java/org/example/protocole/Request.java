package org.example.protocol;

import org.example.protocole.RequestType;

import java.io.Serializable;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    private RequestType type;
    private String senderUsername;
    private String receiverUsername; // Utilisé uniquement pour SEND_MESSAGE
    private String content;          // Contient le mot de passe pour LOGIN, ou le texte pour SEND_MESSAGE

    // Constructeur pour une requête simple (ex: GET_USERS, LOGOUT)
    public Request(RequestType type, String senderUsername) {
        this.type = type;
        this.senderUsername = senderUsername;
    }

    // Constructeur pour le LOGIN ou l'envoi de message
    public Request(RequestType type, String senderUsername, String content) {
        this.type = type;
        this.senderUsername = senderUsername;
        this.content = content;
    }

    // Constructeur complet pour envoyer un message privé à quelqu'un
    public Request(RequestType type, String senderUsername, String receiverUsername, String content) {
        this.type = type;
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.content = content;
    }

    // Getters
    public RequestType getType() { return type; }
    public String getSenderUsername() { return senderUsername; }
    public String getReceiverUsername() { return receiverUsername; }
    public String getContent() { return content; }
}