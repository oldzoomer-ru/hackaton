package ru.oldzoomer.hackaton.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("hackathons")
@Getter
@Setter
public class Hackathon {

    @Id
    private Long id;
    private String name;
    private String track;
    private LocalDate registrationDeadline;
    private LocalDate onlineStageStart;
    private LocalDate onlineStageEnd;
    private LocalDate resultsDate;
    private LocalDate finalStageStart;
    private LocalDate finalStageEnd;
    private String status; // REGISTERING, ONLINE_STAGE, FINAL, COMPLETED
    private String description;
    private LocalDateTime createdAt;

    public Hackathon() {
        this.createdAt = LocalDateTime.now();
        this.status = "REGISTERING";
    }
}
