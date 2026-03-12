package org.example;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.client.Client;

public class Main extends Application {

    public static void main(String[] args) {
        // --- Hibernate / DB initialisation (always runs) ---
        System.out.println("Démarrage et initialisation de Hibernate...");
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("chat_asso_pu");
             EntityManager em = emf.createEntityManager()) {
            System.out.println("Connexion à PostgreSQL réussie !");
            System.out.println("Les tables 'users' et 'messages' ont été générées avec succès.");
        } catch (Exception e) {
            System.err.println("Erreur lors de la connexion à la base de données :");
            e.printStackTrace();
        }

        // --- Mode selection ---
        if (args.length > 0 && args[0].equalsIgnoreCase("cli")) {
            new Client("127.0.0.1", 5000).start();
        } else {
            launch(args); // triggers start(Stage) below
        }
    }

    // JavaFX entry point — only reached in GUI mode
    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(
                getClass().getResource("/app/loginView.fxml"));

        primaryStage.setTitle("Messagerie de l'Association – Connexion");
        primaryStage.setScene(new Scene(root));
        primaryStage.setResizable(false);
        primaryStage.show();
    }
}