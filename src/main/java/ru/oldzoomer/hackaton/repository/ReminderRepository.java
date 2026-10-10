package ru.oldzoomer.hackaton.repository;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.oldzoomer.hackaton.entity.Reminder;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReminderRepository extends CrudRepository<Reminder, Long> {

    @Query("SELECT * FROM reminders WHERE executed = false AND scheduled_at <= :now ORDER BY scheduled_at ASC")
    List<Reminder> findPendingReminders(@Param("now") LocalDateTime now);

    @Query("SELECT * FROM reminders WHERE telegram_user_id = :userId AND executed = false ORDER BY scheduled_at ASC")
    List<Reminder> findPendingForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("UPDATE reminders SET executed = true WHERE id = :id")
    int markExecuted(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query("DELETE FROM reminders WHERE executed = true AND created_at < :before")
    int cleanupOldExecuted(@Param("before") LocalDateTime before);
}
