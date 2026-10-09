package ru.oldzoomer.hackaton.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Setter
@Getter
@Table("users")
public class User {

    @Id
    private Long id;
    private Long telegramId;
    private String username;
    private String firstName;
    private String lastName;
    private LocalDateTime createdAt;

    public User() {
        this.createdAt = LocalDateTime.now();
    }

    public User(Long telegramId, String username, String firstName, String lastName) {
        this();
        this.telegramId = telegramId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
