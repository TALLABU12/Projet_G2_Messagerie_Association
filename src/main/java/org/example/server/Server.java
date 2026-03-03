package org.example.server;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {
    // Liste thread-safe pour stocker tous les clients actifs
    private static final List<ClientHandler> clientsConnectes = new CopyOnWriteArrayList<>();
    private final int port;

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serveur de Chat démarré sur le port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Nouvelle connexion : " + socket.getPort());

                // Création et lancement du gestionnaire pour ce client
                ClientHandler clientHandler = new ClientHandler(socket);
                clientsConnectes.add(clientHandler); // On l'ajoute à la liste
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur serveur : " + e.getMessage());
        }
    }

    // Méthode cruciale : elle envoie un message à TOUS les clients SAUF l'expéditeur
    public static void broadcast(String message, ClientHandler expediteur) {
        for (ClientHandler client : clientsConnectes) {
            if (client != expediteur) {
                client.envoyerMessage(message);
            }
        }
    }

    // Retirer un client de la liste quand il se déconnecte
    public static void retirerClient(ClientHandler client) {
        clientsConnectes.remove(client);
    }

    public static void main(String[] args) {
        new Server(5000).start();
    }
}