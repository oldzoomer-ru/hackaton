package ru.oldzoomer.hackaton.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("reminders")
@Getter
@Setter
public class Reminder {

    @Id
    private Long id;
    private Long telegramUserId;
    private LocalDateTime scheduledAt;
    private String text;
    private String type; // DEADLINE_7D, DEADLINE_3D, DEADLINE_1D, DEADLINE_TODAY, OVERDUE_DAILY, DAILY_SUMMARY
    private boolean executed;
    private LocalDateTime createdAt;

    public Reminder() {
        this.createdAt = LocalDateTime.now();
        this.executed = false;
    }
}
