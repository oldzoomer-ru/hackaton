package ru.oldzoomer.hackaton.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для управления ролями участников команды.
 * Хранит роли пользователей в памяти (для production можно заменить на DB).
 */
@Service
@Log4j2
public class UserTeamService {

    private final ConcurrentHashMap<Long, String> userRoles = new ConcurrentHashMap<>();

    /**
     * Установить роль пользователя.
     */
    public void setRole(Long telegramId, String role) {
        userRoles.put(telegramId, role);
        log.info("User {} role set to: {}", telegramId, role);
    }

    /**
     * Получить роль пользователя.
     */
    public String getRole(Long telegramId) {
        return userRoles.get(telegramId);
    }

    /**
     * Удалить роль пользователя.
     */
    public void removeRole(Long telegramId) {
        userRoles.remove(telegramId);
        log.info("User {} role removed", telegramId);
    }

    /**
     * Получить всех пользователей с ролями.
     */
    public ConcurrentHashMap<Long, String> getAllRoles() {
        return new ConcurrentHashMap<>(userRoles);
    }

    /**
     * Очистить все роли.
     */
    public void clearAll() {
        userRoles.clear();
        log.info("All user roles cleared");
    }
}
