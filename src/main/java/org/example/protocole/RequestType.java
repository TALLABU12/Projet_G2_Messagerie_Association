package org.example.protocole;

public enum RequestType {
    LOGIN,          // Demande de connexion
    LOGOUT,         // Demande de déconnexion
    SEND_MESSAGE,   // Envoi d'un message privé
    GET_USERS,
    GET_HISTORY,
    GET_ALL_USERS
}
