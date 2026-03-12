package org.example.client;

import org.example.entity.Message;
import org.example.entity.User;
import org.example.protocole.Request;
import org.example.protocole.RequestType;
import org.example.protocole.Response;
import org.example.protocole.ResponseStatus;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;

public class Client {
    private final String host;
    private final int port;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        try {
            Socket socket = new Socket(host, port);

            // ATTENTION : Toujours initialiser le ObjectOutputStream AVANT le ObjectInputStream
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            Scanner scanner = new Scanner(System.in);

            System.out.println("=== Messagerie de l'Association ===");
            System.out.print("Nom d'utilisateur : ");
            this.username = scanner.nextLine();
            System.out.print("Mot de passe : ");
            String password = scanner.nextLine();

            // 1. Envoi de la requête d'authentification
            Request loginRequest = new Request(RequestType.LOGIN, username, password);
            out.writeObject(loginRequest);
            out.flush();

            // 2. Attente de la réponse du serveur
            Response loginResponse = (Response) in.readObject();

            if (loginResponse.getStatus() == ResponseStatus.SUCCESS) {
                System.out.println("✅ " + loginResponse.getMessage());

                // 3. Lancement du Thread pour écouter les messages entrants en arrière-plan
                Thread listenerThread = new Thread(new IncomingReader());
                listenerThread.start();

                System.out.println("-> Pour envoyer un message, tapez : destinataire:votre_message");
                System.out.println("-> Pour quitter, tapez : quitter");

                // 4. Boucle principale pour envoyer des messages
                while (true) {
                    String input = scanner.nextLine();

                    // Quitter
                    // Dans le Client.java, dans la boucle while :

                    if ("quitter".equalsIgnoreCase(input)) {
                        out.writeObject(new Request(RequestType.LOGOUT, username));
                        out.flush();
                        break;
                    } else if ("allusers".equalsIgnoreCase(input)) {
                        // NOUVEAU : Demander la liste complète des membres
                        out.writeObject(new Request(RequestType.GET_ALL_USERS, username));
                        out.flush();
                    }
                    // Historique
                    else if (input.startsWith("history:")) {
                        String target = input.split(":", 2)[1].trim();
                        Request histReq = new Request(RequestType.GET_HISTORY, username, target, null);
                        out.writeObject(histReq);
                        out.flush();
                    }

                    // Envoi message
                    else if (input.contains(":")) {
                        String[] parts = input.split(":", 2);
                        String receiver = parts[0].trim();
                        String content = parts[1].trim();

                        Request sendReq = new Request(RequestType.SEND_MESSAGE, username, receiver, content);
                        out.writeObject(sendReq);
                        out.flush();
                    }

                    // Mauvais format
                    else {
                        System.out.println("⚠️ Format incorrect.");
                        System.out.println("destinataire:message");
                        System.out.println("history:destinataire");
                    }
                }

            } else {
                // Échec de la connexion
                System.out.println("❌ Accès refusé : " + loginResponse.getMessage());
            }

            socket.close();
            scanner.close();

        } catch (Exception e) {
            // RG10 : Afficher une erreur et passer hors ligne en cas de perte réseau
            System.err.println("❌ Perte de connexion avec le serveur : " + e.getMessage());
        }
    }

    // --- Thread interne pour lire les réponses du serveur sans bloquer la saisie ---
        private class IncomingReader implements Runnable {
            @Override
            public void run() {
                try {
                    while (true) {
                        Response response = (Response) in.readObject();

                        if (response.getStatus() == ResponseStatus.SUCCESS) {

                            // 📌 Cas 1 : Historique
                            // Dans IncomingReader du Client.java :
                            if (response.getData() instanceof List) {
                                List<?> dataList = (List<?>) response.getData();

                                if (dataList.isEmpty()) {
                                    System.out.println("La liste est vide.");
                                } else if (dataList.get(0) instanceof Message) {
                                    // C'est l'historique (votre code existant)
                                    System.out.println("\n--- Historique des messages ---");
                                    for (Object obj : dataList) {
                                        Message m = (Message) obj;
                                        System.out.println("[" + m.getDateEnvoi().toLocalTime() + "] " + m.getSender().getUsername() + " : " + m.getContenu());
                                    }
                                    System.out.println("-------------------------------");
                                } else if (dataList.get(0) instanceof User) {
                                    // NOUVEAU : C'est la liste des utilisateurs (RG13)
                                    System.out.println("\n--- Liste complète des membres ---");
                                    for (Object obj : dataList) {
                                        User u = (User) obj;
                                        System.out.println("- " + u.getUsername() + " [Rôle: " + u.getRole() + "] - Statut: " + u.getStatus());
                                    }
                                    System.out.println("----------------------------------");
                                }
                            }

                            // 📌 Cas 2 : Nouveau message entrant
                            else if (response.getData() instanceof Message) {

                                Message msg = (Message) response.getData();
                                System.out.println("\n📩 [Nouveau Message] de "
                                        + msg.getSender().getUsername()
                                        + " : "
                                        + msg.getContenu());
                            }

                            // 📌 Cas 3 : Message simple du serveur
                            else {
                                System.out.println("\n✅ " + response.getMessage());
                            }

                        } else {
                            // ❌ Cas erreur
                            System.out.println("\n⚠️ Erreur : " + response.getMessage());
                        }

                        System.out.print("-> ");
                    }

                } catch (Exception e) {
                    System.out.println("\n❌ Déconnecté du serveur.");
                }
            }
        }

    public static void main(String[] args) {
        new Client("127.0.0.1", 5000).start();
    }
}