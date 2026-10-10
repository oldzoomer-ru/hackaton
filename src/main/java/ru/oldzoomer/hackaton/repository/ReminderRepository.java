package ru.oldzoomer.hackaton.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oldzoomer.hackaton.entity.Reminder;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReminderRepository extends CrudRepository<Reminder, Long> {

    @Query("SELECT * FROM reminders WHERE executed = false AND scheduled_at <= :now ORDER BY scheduled_at ASC")
    List<Reminder> findPendingReminders(@Param("now") LocalDateTime now);

    @Query("SELECT * FROM reminders WHERE telegram_user_id = :userId AND executed = false ORDER BY scheduled_at ASC")
    List<Reminder> findPendingForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("UPDATE reminders SET executed = true WHERE id = :id")
    void markExecuted(@Param("id") Long id);

    @Query("DELETE FROM reminders WHERE executed = true AND created_at < :before")
    void cleanupOldExecuted(@Param("before") LocalDateTime before);
}
