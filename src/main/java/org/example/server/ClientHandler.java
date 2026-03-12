package org.example.server;

import org.example.entity.Message;
import org.example.entity.User;
import org.example.enums.Role;
import org.example.enums.UserStatus;
import org.example.service.AuthService ;
import org.example.protocole.Request;
import org.example.protocole.Response;
import org.example.protocole.ResponseStatus;
import org.example.service.MessageService;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String loggedInUser = null; // Garde en mémoire qui est connecté sur ce thread
    private final AuthService authService = new AuthService();
    private final MessageService messageService = new MessageService();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // Initialisation des flux d'objets
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                // Lecture de la requête envoyée par le client
                Request request = (Request) in.readObject();

                switch (request.getType()) {
                    case LOGIN:
                        handleLogin(request);
                        break;
                    case SEND_MESSAGE:
                        handleSendMessage(request);
                        break;
                    case LOGOUT:
                        handleLogout();
                        return; // Met fin au thread
                    case GET_HISTORY:
                        handleGetHistory(request);
                        break;
                    case GET_USERS:
                        handleGetUsers(request);
                        break;
                    case GET_ALL_USERS:
                        handleGetAllUsers(request);
                        break;
                }
            }
        } catch (EOFException e) {
            System.out.println("Client déconnecté brutalement.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // RG4 & RG10 : Si le client crashe ou perd la connexion, on le passe OFFLINE
            handleLogout();
        }
    }
    private void handleGetUsers(Request request) {
        try {
            // On récupère uniquement les utilisateurs en ligne
            List<User> utilisateursEnLigne = authService.getOnlineUsers();

            // On renvoie cette liste spécifique au client
            out.writeObject(new Response(ResponseStatus.SUCCESS, "Liste des membres connectés", utilisateursEnLigne));
            out.flush();

            // Optionnel : Journalisation (RG12)
            System.out.println("[LOG] " + request.getSenderUsername() + " a demandé la liste des membres connectés.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void handleLogin(Request request) {
        try {
            String username = request.getSenderUsername();
            String password = request.getContent();

            // RG3 : Vérifier s'il n'est pas déjà connecté
            if (SessionManager.isUserConnected(username)) {
                out.writeObject(new Response(ResponseStatus.ERROR, "Utilisateur déjà connecté ailleurs."));
                return;
            }

            // RG2 : Tentative d'authentification
            User user = authService.authenticate(username, password);

            if (user != null) {
                loggedInUser = username;
                SessionManager.addSession(username, out);

                // RG4 : Passer le statut à ONLINE en base de données
                authService.updateStatus(loggedInUser, UserStatus.ONLINE);
                System.out.println("L'utilisateur " + username + " s'est connecté. [Rôle: " + user.getRole() + "]");

                // Renvoyer le succès au client avec l'objet User (sans le mot de passe de préférence)
                out.writeObject(new Response(ResponseStatus.SUCCESS, "Connexion réussie", user));
            } else {
                out.writeObject(new Response(ResponseStatus.ERROR, "Nom d'utilisateur ou mot de passe incorrect."));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleLogout() {
        if (loggedInUser != null) {
            // Retirer de la liste des sessions actives
            SessionManager.removeSession(loggedInUser);
            // RG4 : Passer le statut à OFFLINE en base
            authService.updateStatus(loggedInUser, UserStatus.OFFLINE);
            System.out.println("L'utilisateur " + loggedInUser + " s'est déconnecté.");
            loggedInUser = null;
        }
        try { socket.close(); } catch (Exception e) { /* ignoré */ }
    }
    private void handleSendMessage(Request request) {
        try {
            String senderUsername = request.getSenderUsername();
            String receiverUsername = request.getReceiverUsername();
            String content = request.getContent();

            // RG7 : Vérification du contenu
            if (content == null || content.trim().isEmpty()) {
                out.writeObject(new Response(ResponseStatus.ERROR, "Le message ne peut pas être vide."));
                return;
            }

            // 1. Récupérer les entités depuis la base de données
            User sender = messageService.getUserByUsername(senderUsername);
            User receiver = messageService.getUserByUsername(receiverUsername);

            // RG5 : Vérifier si le destinataire existe
            if (receiver == null) {
                out.writeObject(new Response(ResponseStatus.ERROR, "Le destinataire '" + receiverUsername + "' n'existe pas."));
                return;
            }

            // 2. Déterminer si le destinataire est connecté (pour le statut du message)
            boolean isReceiverOnline = SessionManager.isUserConnected(receiverUsername);
            Message.MessageStatut statutMessage = isReceiverOnline ? Message.MessageStatut.RECU : Message.MessageStatut.ENVOYE;

            // 3. Sauvegarder le message en base de données (Valide RG6)
            Message savedMessage = messageService.saveMessage(sender, receiver, content, statutMessage);

            if (savedMessage != null) {
                // Confirmer à l'expéditeur que le message est parti
                out.writeObject(new Response(ResponseStatus.SUCCESS, "Message envoyé à " + receiverUsername));

                // 4. Si le destinataire est en ligne, on lui pousse le message en temps réel !
                if (isReceiverOnline) {
                    ObjectOutputStream receiverOut = SessionManager.getOutputStream(receiverUsername);

                    // On prépare une réponse spéciale contenant le message entrant
                    Response incomingMessage = new Response(
                            ResponseStatus.SUCCESS,
                            "Nouveau message de " + senderUsername,
                            savedMessage // On passe l'objet Message complet
                    );

                    // Envoi au destinataire
                    receiverOut.writeObject(incomingMessage);
                    receiverOut.flush();
                }
            } else {
                out.writeObject(new Response(ResponseStatus.ERROR, "Erreur lors de la sauvegarde du message."));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void handleGetAllUsers(Request request) {
        try {
            String requesterUsername = request.getSenderUsername();

            // On récupère l'utilisateur en base pour vérifier son rôle réel
            User requester = messageService.getUserByUsername(requesterUsername);

            if (requester == null) {
                out.writeObject(new Response(ResponseStatus.ERROR, "Utilisateur introuvable."));
                out.flush();
                return;
            }

            // RG13 : Vérification stricte du rôle (Le Backend bloque !)
            if (requester.getRole() != Role.ORGANISATEUR) { // Adaptez le chemin de votre Enum Role
                out.writeObject(new Response(ResponseStatus.ERROR, "Accès refusé : Seul un ORGANISATEUR peut consulter la liste complète des inscrits."));
                out.flush();

                // Optionnel : Tracer la tentative d'accès non autorisée
                System.out.println("[ALERTE] " + requesterUsername + " a tenté d'accéder à la liste complète sans les droits.");
                return;
            }

            // Si c'est bien un organisateur, on récupère tout le monde
            List<User> tousLesMembres = authService.getAllUsers();

            // On renvoie la liste
            out.writeObject(new Response(ResponseStatus.SUCCESS, "Liste complète récupérée", tousLesMembres));
            out.flush();

            System.out.println("[LOG] L'organisateur " + requesterUsername + " a consulté la liste complète des membres.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void handleGetHistory(Request request) {
        try {
            String senderUsername = request.getSenderUsername();
            String receiverUsername = request.getReceiverUsername(); // Avec qui on veut voir l'historique

            User sender = messageService.getUserByUsername(senderUsername);
            User receiver = messageService.getUserByUsername(receiverUsername);

            if (receiver == null) {
                out.writeObject(new Response(ResponseStatus.ERROR, "Utilisateur introuvable pour l'historique."));
                return;
            }

            // Récupération de la liste triée
            List<Message> history = messageService.getHistory(sender, receiver);

            // On renvoie la liste dans l'objet Response (dans l'attribut "data")
            out.writeObject(new Response(ResponseStatus.SUCCESS, "Historique récupéré", history));
            out.flush();

            // RG12 : Journalisation des actions sur le serveur
            System.out.println("[LOG] " + senderUsername + " a consulté son historique avec " + receiverUsername);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void envoyerMessage(String message) {
    }
}