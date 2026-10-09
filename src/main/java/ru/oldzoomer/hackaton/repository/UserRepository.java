package ru.oldzoomer.hackaton.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oldzoomer.hackaton.entity.User;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends CrudRepository<User, Long> {

    @Query("SELECT * FROM users WHERE telegram_id = :telegramId")
    Optional<User> findByTelegramId(@Param("telegramId") Long telegramId);

    @Query("SELECT * FROM users ORDER BY created_at DESC LIMIT 100")
    List<User> findRecentUsers();
}
