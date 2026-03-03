package org.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.enums.Role;
import org.example.enums.UserStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "users") @Setter @Getter
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(nullable = false)
    private LocalDateTime dateCreation;


    public User() {
        this.dateCreation = LocalDateTime.now();
        this.status = UserStatus.OFFLINE;
    }

}
