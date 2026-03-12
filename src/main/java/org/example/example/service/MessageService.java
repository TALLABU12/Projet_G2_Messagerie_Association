package org.example.service;


import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import org.example.entity.Message;
import org.example.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public class MessageService {
    private static final EntityManagerFactory emf = Persistence.createEntityManagerFactory("chat_asso_pu");

    // Permet de retrouver un utilisateur à partir de son pseudo (pour vérifier RG5)
    public User getUserByUsername(String username) {
        EntityManager em = emf.createEntityManager();
        try {
            TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class);
            query.setParameter("username", username);
            return query.getResultStream().findFirst().orElse(null);
        } finally {
            em.close();
        }
    }

    // Sauvegarde le message en base de données
    public Message saveMessage(User sender, User receiver, String contenu, Message.MessageStatut statut) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();

            Message message = new Message();
            message.setSender(sender);
            message.setReceiver(receiver);
            message.setContenu(contenu);
            message.setDateEnvoi(LocalDateTime.now());
            message.setStatut(statut);

            em.persist(message); // Sauvegarde
            em.getTransaction().commit();

            return message;
        } catch (Exception e) {
            em.getTransaction().rollback();
            e.printStackTrace();
            return null;
        } finally {
            em.close();
        }
    }
    public List<Message> getHistory(User user1, User user2) {
        EntityManager em = emf.createEntityManager();
        try {
            // RG8 : L'historique doit être affiché par ordre chronologique (ORDER BY ASC)
            String jpql = "SELECT m FROM Message m " +
                    "WHERE (m.sender = :u1 AND m.receiver = :u2) " +
                    "   OR (m.sender = :u2 AND m.receiver = :u1) " +
                    "ORDER BY m.dateEnvoi ASC";

            TypedQuery<Message> query = em.createQuery(jpql, Message.class);
            query.setParameter("u1", user1);
            query.setParameter("u2", user2);

            return query.getResultList();
        } finally {
            em.close();
        }
    }
}
