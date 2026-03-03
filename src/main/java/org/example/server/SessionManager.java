package org.example.server;

import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    // Associe le nom d'utilisateur (username) à son flux de sortie pour lui envoyer des messages
    private static final ConcurrentHashMap<String, ObjectOutputStream> activeSessions = new ConcurrentHashMap<>();

    public static synchronized boolean isUserConnected(String username) {
        return activeSessions.containsKey(username);
    }

    public static synchronized void addSession(String username, ObjectOutputStream out) {
        activeSessions.put(username, out);
    }

    public static synchronized void removeSession(String username) {
        activeSessions.remove(username);
    }

    public static ObjectOutputStream getOutputStream(String username) {
        return activeSessions.get(username);
    }
}
