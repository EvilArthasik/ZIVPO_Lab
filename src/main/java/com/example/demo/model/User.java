package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "app_users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @JsonIgnore
    @Column(length = 120)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Role role = Role.USER;

    @PrePersist
    void onCreate() {
        if (role == null) {
            role = Role.USER;
        }
    }

    public enum Role {
        USER, MANAGER, ADMIN
    }
}
