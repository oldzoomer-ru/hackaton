package ru.oldzoomer.hackaton.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oldzoomer.hackaton.entity.Task;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskRepository extends CrudRepository<Task, Long> {

    @Query("SELECT * FROM tasks WHERE hackathon_id = :hackathonId ORDER BY created_at DESC")
    List<Task> findByHackathonId(@Param("hackathonId") Long hackathonId);

    @Query("SELECT * FROM tasks WHERE status = 'TODO' AND deadline <= :today ORDER BY deadline ASC")
    List<Task> findOverdueTasks(@Param("today") LocalDate today);

    @Query("SELECT * FROM tasks WHERE deadline BETWEEN :today AND :deadline ORDER BY deadline ASC")
    List<Task> findTasksByDeadlineRange(@Param("today") LocalDate today, @Param("deadline") LocalDate deadline);

    @Query("SELECT * FROM tasks WHERE status = 'IN_PROGRESS' ORDER BY created_at DESC")
    List<Task> findInProgressTasks();

    @Query("SELECT * FROM tasks WHERE status = 'DONE' AND completed_at >= :since ORDER BY completed_at DESC")
    List<Task> findDoneSince(@Param("since") LocalDate since);
}
