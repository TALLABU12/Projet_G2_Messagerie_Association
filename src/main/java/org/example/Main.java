package org.example;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;


public class Main {
    public static void main(String[] args) {
        System.out.println("Démarrage du serveur et initialisation de Hibernate...");

        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("chat_asso_pu");
             EntityManager em = emf.createEntityManager()) {

            System.out.println("Connexion à PostgreSQL réussie !");
            System.out.println("Les tables 'users' et 'messages' ont été générées  avec succès.");

        } catch (Exception e) {
            System.err.println("Erreur lors de la connexion à la base de données :");
            e.printStackTrace();
        }
    }
}