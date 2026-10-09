package ru.oldzoomer.hackaton.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("tasks")
@Getter
@Setter
public class Task {

    @Id
    private Long id;
    private Long hackathonId;
    private String title;
    private String status; // TODO, IN_PROGRESS, DONE, BLOCKED
    private String assignee;
    private LocalDate deadline;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String notes;

    public Task() {
        this.createdAt = LocalDateTime.now();
        this.status = "TODO";
    }
}
