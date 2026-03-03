package org.example.protocole;

import java.io.Serializable;

public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    private ResponseStatus status;
    private String message; // Message d'information ou d'erreur
    private Object data;    // Pour renvoyer des objets complexes (ex: List<User>, List<Message>)

    public Response(ResponseStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public Response(ResponseStatus status, String message, Object data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    // Getters
    public ResponseStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public Object getData() { return data; }
}