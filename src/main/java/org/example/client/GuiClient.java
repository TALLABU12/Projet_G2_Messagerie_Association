package org.example.client;

import javafx.application.Platform;
import org.example.protocole.Request;
import org.example.protocole.RequestType;
import org.example.protocole.Response;
import org.example.protocole.ResponseStatus;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Minimal UI-friendly wrapper for the socket protocol.
 * - connectAndLogin(...) performs LOGIN handshake and returns a Response (or throws).
 * - Starts a background listener and forwards server Responses via onResponse callback on the FX thread.
 */
public class GuiClient {
    private final String host;
    private final int port;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    private Consumer<Response> onResponse;

    public GuiClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setOnResponse(Consumer<Response> onResponse) {
        this.onResponse = onResponse;
    }

    /**
     * Connects to server, sends login request and returns the server Response.
     * This method blocks — call it from a background thread.
     */
    public Response connectAndLogin(String username, String password) throws Exception {
        this.socket = new Socket(host, port);
        // Important: ObjectOutputStream before ObjectInputStream
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in  = new ObjectInputStream(socket.getInputStream());

        Request loginReq = new Request(RequestType.LOGIN, username, password);
        out.writeObject(loginReq);
        out.flush();

        Object obj = in.readObject();
        if (!(obj instanceof Response)) {
            throw new IllegalStateException("Réponse inattendue du serveur");
        }
        Response resp = (Response) obj;

        if (resp.getStatus() == ResponseStatus.SUCCESS) {
            this.username = username;
            // Start listener thread to receive server pushes
            Thread t = new Thread(this::incomingLoop, "GuiClient-Incoming");
            t.setDaemon(true);
            t.start();
        }
        return resp;
    }

    private void incomingLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Object obj = in.readObject();
                if (obj instanceof Response && onResponse != null) {
                    Response r = (Response) obj;
                    Platform.runLater(() -> onResponse.accept(r));
                }
            }
        } catch (Exception e) {
            if (onResponse != null) {
                // Use the two-arg constructor — no setters needed, no unknown enum values
                Response disconnected = new Response(
                        ResponseStatus.ERROR,
                        "❌ Déconnecté du serveur : " + e.getMessage()
                );
                Platform.runLater(() -> onResponse.accept(disconnected));
            }
            try { close(); } catch (Exception ignored) {}
        }
    }

    public void sendMessage(String receiver, String content) throws Exception {
        Request r = new Request(RequestType.SEND_MESSAGE, username, receiver, content);
        out.writeObject(r);
        out.flush();
    }

    public void requestHistory(String target) throws Exception {
        Request r = new Request(RequestType.GET_HISTORY, username, target, null);
        out.writeObject(r);
        out.flush();
    }

    public void requestAllUsers() throws Exception {
        Request r = new Request(RequestType.GET_ALL_USERS, username);
        out.writeObject(r);
        out.flush();
    }

    public void logout() {
        try {
            if (out != null) {
                out.writeObject(new Request(RequestType.LOGOUT, username));
                out.flush();
            }
        } catch (Exception ignored) {}
        finally {
            try { close(); } catch (Exception ignored) {}
        }
    }

    public void close() throws Exception {
        if (in  != null) in.close();
        if (out != null) out.close();
        if (socket != null) socket.close();
    }

    public String getUsername() { return username; }
}