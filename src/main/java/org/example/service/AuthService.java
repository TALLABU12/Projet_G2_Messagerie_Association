package org.example.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import org.example.entity.User;
import org.example.enums.UserStatus;

import java.util.List;
// import org.mindrot.jbcrypt.BCrypt; // À ajouter dans votre pom.xml

public class AuthService {
    // "chat_asso_pu" correspond au nom de votre <persistence-unit> dans persistence.xml
    private static final EntityManagerFactory emf = Persistence.createEntityManagerFactory("chat_asso_pu");

    public User authenticate(String username, String rawPassword) {
        EntityManager em = emf.createEntityManager();
        try {
            // 1. Chercher l'utilisateur par son username (RG1)
            TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class);
            query.setParameter("username", username);

            User user = query.getResultStream().findFirst().orElse(null);

            // 2. Vérifier si l'utilisateur existe et si le mot de passe correspond
            if (user != null) {
                // Vérification du mot de passe haché (RG9)
                // boolean passwordMatch = BCrypt.checkpw(rawPassword, user.getPassword());
                boolean passwordMatch = rawPassword.equals(user.getPassword()); // Version simplifiée pour l'instant

                if (passwordMatch) {
                    return user; // Authentification réussie
                }
            }
            return null; // Échec de l'authentification
        } finally {
            em.close();
        }
    }
    public List<User> getAllUsers() {
        EntityManager em = emf.createEntityManager();
        try {
            // Requête JPQL simple pour tout sélectionner
            TypedQuery<User> query = em.createQuery("SELECT u FROM User u", User.class);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            em.close();
        }
    }
    public List<User> getOnlineUsers() {
        EntityManager em = emf.createEntityManager();
        try {
            // On filtre sur la colonne statut
            TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.status = :status", User.class);
            query.setParameter("status", UserStatus.ONLINE); // Assurez-vous d'importer UserStatus
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            em.close();
        }
    }
    public void updateStatus(String username, UserStatus newStatus) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            // Met à jour le statut (ONLINE ou OFFLINE) en base de données (RG4)
            em.createQuery("UPDATE User u SET u.status = :status WHERE u.username = :username")
                    .setParameter("status", newStatus)
                    .setParameter("username", username)
                    .executeUpdate();
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            e.printStackTrace();
        } finally {
            em.close();
        }
    }
}
