package ru.oldzoomer.hackaton.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oldzoomer.hackaton.entity.Hackathon;
import ru.oldzoomer.hackaton.entity.Reminder;
import ru.oldzoomer.hackaton.entity.Task;
import ru.oldzoomer.hackaton.repository.HackathonRepository;
import ru.oldzoomer.hackaton.repository.ReminderRepository;
import ru.oldzoomer.hackaton.repository.TaskRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Сервис для работы с Spring AI — создание, обновление, удаление и запросы
 * к сущностям через естественный язык.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class AiService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ChatClient chatClient;
    private final TaskService taskService;
    private final HackathonRepository hackathonRepo;
    private final TaskRepository taskRepo;
    private final ReminderRepository reminderRepo;
    private final ObjectMapper objectMapper;

    /**
     * Обработка сообщения пользователя в диалоговом режиме.
     * Сохраняет историю и использует контекст для более точных ответов.
     */
    public String processDialog(long chatId, String userInput) {
        log.info("Processing dialog for chat {}: {}", chatId, userInput);

        try {
            // Получаем контекст из базы данных
            String context = buildContext();

            // Формируем системный промпт
            String systemPrompt = buildSystemPrompt(context);

            // Вызываем AI с историей
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userInput)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .call()
                    .content();

            log.info("AI response for chat {}: {}", chatId, response);

            return response;
        } catch (Exception e) {
            log.error("Error in dialog for chat {}: {}", chatId, userInput, e);
            return "❌ Ошибка при обработке запроса: " + e.getMessage();
        }
    }

    /**
     * Обработка естественного языка для создания/обновления/удаления сущностей.
     * Возвращает результат операции.
     */
    public String processNaturalLanguage(String userInput) {
        log.info("Processing natural language input: {}", userInput);

        try {
            String context = buildContext();
            String systemPrompt = buildActionSystemPrompt(context);

            // Запрашиваем JSON-ответ
            String aiResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userInput)
                    .call()
                    .content();

            log.info("AI raw response: {}", aiResponse);

            if (aiResponse == null || aiResponse.isBlank()) {
                return "❌ AI не вернул ответ. Попробуйте переформулировать.";
            }

            // Парсим JSON
            Map<String, Object> data = parseJsonResponse(aiResponse);

            if (data == null) {
                log.warn("Could not parse AI response as JSON: {}", aiResponse);
                return "❌ Не удалось обработать ответ AI. Попробуйте переформулировать запрос.";
            }

            String action = (String) data.get("action");
            if (action == null) {
                return "❓ Не удалось определить действие. Уточните ваш запрос.";
            }

            // Выполняем действие
            return switch (action.toLowerCase()) {
                case "create_hackathon" -> createHackathon(data);
                case "create_task" -> createTask(data);
                case "update_hackathon" -> updateHackathon(data);
                case "update_task" -> updateTask(data);
                case "delete_hackathon" -> deleteHackathon(data);
                case "delete_task" -> deleteTask(data);
                case "create_reminder" -> createReminder(data);
                case "ask_question" -> (String) data.getOrDefault("question", "Уточните ваш запрос.");
                default -> "❓ Неизвестное действие '" + action + "'. Попробуйте переформулировать.";
            };

        } catch (Exception e) {
            log.error("Error processing natural language: {}", userInput, e);
            return "❌ Ошибка при обработке запроса: " + e.getMessage();
        }
    }

    /**
     * Построение контекста из базы данных.
     */
    private String buildContext() {
        List<Hackathon> hackathons = hackathonRepo.findAllOrdered();
        Iterator<Task> tasks = taskRepo.findAll().iterator();
        Iterator<Reminder> reminders = reminderRepo.findAll().iterator();

        StringBuilder sb = new StringBuilder();

        sb.append("=== ХАКАТОНЫ ===\n");
        if (hackathons.isEmpty()) {
            sb.append("(нет хакатонов)\n");
        } else {
            for (Hackathon h : hackathons) {
                sb.append("- name: ").append(h.getName()).append("\n");
                sb.append("  id: ").append(h.getId()).append("\n");
                if (h.getTrack() != null) sb.append("  track: ").append(h.getTrack()).append("\n");
                if (h.getRegistrationDeadline() != null) sb.append("  registrationDeadline: ").append(h.getRegistrationDeadline()).append("\n");
                if (h.getDescription() != null) sb.append("  description: ").append(h.getDescription()).append("\n");
                sb.append("  status: ").append(h.getStatus()).append("\n");
                sb.append("\n");
            }
        }

        sb.append("=== ЗАДАЧИ ===\n");
        if (!tasks.hasNext()) {
            sb.append("(нет задач)\n");
        } else {
            while (tasks.hasNext()) {
                Task t = tasks.next();
                sb.append("- title: ").append(t.getTitle()).append("\n");
                sb.append("  id: ").append(t.getId()).append("\n");
                sb.append("  hackathonId: ").append(t.getHackathonId()).append("\n");
                if (t.getAssignee() != null) sb.append("  assignee: ").append(t.getAssignee()).append("\n");
                if (t.getDeadline() != null) sb.append("  deadline: ").append(t.getDeadline()).append("\n");
                sb.append("  status: ").append(t.getStatus()).append("\n");
                sb.append("\n");
            }
        }

        sb.append("=== НАПОМИНАНИЯ ===\n");
        if (!tasks.hasNext()) {
            sb.append("(нет напоминаний)\n");
        } else {
            while (reminders.hasNext()) {
                Reminder r = reminders.next();
                sb.append("- id: ").append(r.getId()).append("\n");
                sb.append("  text: ").append(r.getText()).append("\n");
                sb.append("  type: ").append(r.getType()).append("\n");
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Системный промпт для диалогового режима.
     */
    private String buildSystemPrompt(String context) {
        return """
                Ты — умный ассистент для управления хакатонами, задачами и напоминаниями.
                Ты работаешь в диалоговом режиме и можешь:
                1. Отвечать на вопросы о хакатонах, задачах и напоминаниях
                2. Создавать, обновлять и удалять сущности
                3. Помогать с планированием и организацией

                КОНТЕКСТ (данные из БД):
                %s

                ПРАВИЛА:
                - Отвечай на русском языке, кратко и по делу
                - Если пользователь хочет создать хакатон/задачу — используй JSON-формат
                - Если пользователь хочет обновить — укажи что именно менять
                - Если пользователь хочет удалить — подтверди удаление
                - Если данных недостаточно — задай уточняющий вопрос
                - Формат дат: дд.мм.гггг
                - Статус хакатона по умолчанию: REGISTERING
                - Статус задачи по умолчанию: TODO

                Если нужно создать/обновить/удалить сущность, отвечай ТОЛЬКО JSON:
                {"action": "create_hackathon" | "create_task" | "update_hackathon" | "update_task" | "delete_hackathon" | "delete_task" | "create_reminder" | "ask_question", ...}

                create_hackathon: {"action": "create_hackathon", "name": "...", "track": "...", "registrationDeadline": "dd.mm.yyyy", "description": "..."}
                create_task: {"action": "create_task", "title": "...", "assignee": "...", "deadline": "dd.mm.yyyy", "hackathonName": "..."}
                update_hackathon: {"action": "update_hackathon", "name": "...", "field": "...", "value": "..."}
                update_task: {"action": "update_task", "title": "...", "field": "...", "value": "..."}
                delete_hackathon: {"action": "delete_hackathon", "name": "..."}
                delete_task: {"action": "delete_task", "title": "..."}
                create_reminder: {"action": "create_reminder", "text": "...", "scheduledAt": "dd.mm.yyyy HH:mm"}
                ask_question: {"action": "ask_question", "question": "..."}
                """.formatted(context);
    }

    /**
     * Системный промпт для действий (создание/обновление/удаление).
     */
    private String buildActionSystemPrompt(String context) {
        return """
                Ты — ассистент для управления хакатонами. Твоя задача — извлекать данные из сообщений
                пользователя и выполнять действия с сущностями.

                КОНТЕКСТ (данные из БД):
                %s

                ТЫ МОЖЕШЬ:
                1. Создавать хакатоны и задачи
                2. Обновлять существующие хакатоны и задачи
                3. Удалять хакатоны и задачи
                4. Создавать напоминания
                5. Задавать уточняющие вопросы

                ПРАВИЛА:
                - name/title — обязательные поля
                - Если пользователь говорит "обнови" или "измени" — используй update
                - Если пользователь говорит "удали" или "удаль" — используй delete
                - Если ключевые данные отсутствуют — используй ask_question
                - Для update: укажи name/title и поле, которое нужно изменить
                - Для delete: укажи name/title сущности

                ВАЖНО: Отвечай ТОЛЬКО валидным JSON, без дополнительного текста.

                Форматы:
                {"action": "create_hackathon", "name": "...", "track": "...", "registrationDeadline": "dd.mm.yyyy", "description": "..."}
                {"action": "create_task", "title": "...", "assignee": "...", "deadline": "dd.mm.yyyy", "hackathonName": "..."}
                {"action": "update_hackathon", "name": "...", "field": "track|description|registrationDeadline", "value": "..."}
                {"action": "update_task", "title": "...", "field": "status|assignee|deadline|notes", "value": "..."}
                {"action": "delete_hackathon", "name": "..."}
                {"action": "delete_task", "title": "..."}
                {"action": "create_reminder", "text": "...", "scheduledAt": "dd.mm.yyyy HH:mm"}
                {"action": "ask_question", "question": "..."}
                """.formatted(context);
    }

    // ==================== ДЕЙСТВИЯ ====================

    @Transactional
    public String createHackathon(Map<String, Object> data) {
        String name = str(data, "name");
        if (name == null || name.isBlank()) {
            return "❌ Не указано название хакатона.";
        }

        Hackathon hackathon = new Hackathon();
        hackathon.setName(name);
        hackathon.setTrack(str(data, "track"));
        hackathon.setDescription(str(data, "description"));
        hackathon.setRegistrationDeadline(parseDate(data, "registrationDeadline"));
        hackathon.setOnlineStageStart(parseDate(data, "onlineStageStart"));
        hackathon.setOnlineStageEnd(parseDate(data, "onlineStageEnd"));
        hackathon.setResultsDate(parseDate(data, "resultsDate"));
        hackathon.setFinalStageStart(parseDate(data, "finalStageStart"));
        hackathon.setFinalStageEnd(parseDate(data, "finalStageEnd"));

        hackathonRepo.save(hackathon);

        String track = hackathon.getTrack() != null ? ", Трек: " + hackathon.getTrack() : "";
        String deadline = hackathon.getRegistrationDeadline() != null
                ? ", дедлайн регистрации: " + hackathon.getRegistrationDeadline().format(DATE_FORMATTER) : "";

        String msg = String.format("✅ Хакатон '%s' создан.%s%s", name, track, deadline);
        log.info("Hackathon created via AI: {}", name);
        return msg;
    }

    @Transactional
    public String createTask(Map<String, Object> data) {
        String title = str(data, "title");
        if (title == null || title.isBlank()) {
            return "❌ Не указана задача.";
        }

        String hackathonName = str(data, "hackathonName");
        Long hackathonId = resolveHackathonId(hackathonName);

        if (hackathonId == null) {
            return "❌ Хакатон '" + (hackathonName != null ? hackathonName : "<не указан>")
                    + "' не найден. Сначала создайте хакатон.";
        }

        Task task = taskService.addTask(hackathonId, title, str(data, "assignee"),
                parseDate(data, "deadline"), str(data, "notes"));

        String assigneeStr = task.getAssignee() != null ? ", Исполнитель: " + task.getAssignee() : "";
        String deadlineStr = task.getDeadline() != null ? ", дедлайн: " + task.getDeadline().format(DATE_FORMATTER) : "";

        String msg = String.format("✅ Задача '%s' добавлена в хакатон '%s'.%s%s",
                task.getTitle(), hackathonName, assigneeStr, deadlineStr);
        log.info("Task created via AI: {} for hackathon {}", title, hackathonId);
        return msg;
    }

    @Transactional
    public String updateHackathon(Map<String, Object> data) {
        String name = str(data, "name");
        String field = str(data, "field");
        String value = str(data, "value");

        if (name == null || name.isBlank()) {
            return "❌ Не указано название хакатона для обновления.";
        }
        if (field == null || field.isBlank()) {
            return "❌ Не указано поле для обновления. Пример: \"обнови трек у хакатона AI Challenge на Web3\"";
        }
        if (value == null || value.isBlank()) {
            return "❌ Не указано новое значение.";
        }

        Optional<Hackathon> opt = hackathonRepo.findAllOrdered().stream()
                .filter(h -> h.getName() != null && h.getName().equalsIgnoreCase(name))
                .findFirst();

        if (opt.isEmpty()) {
            return "❌ Хакатон '" + name + "' не найден.";
        }

        Hackathon h = opt.get();
        boolean updated = false;

        switch (field.toLowerCase()) {
            case "track" -> {
                h.setTrack(value);
                updated = true;
            }
            case "description" -> {
                h.setDescription(value);
                updated = true;
            }
            case "registrationdeadline" -> {
                LocalDate d = parseDate(Collections.singletonMap("registrationDeadline", value));
                if (d != null) {
                    h.setRegistrationDeadline(d);
                    updated = true;
                }
            }
            default -> log.warn("Unknown field for hackathon update: {}", field);
        }

        if (updated) {
            hackathonRepo.save(h);
            return String.format("✅ Хакатон '%s' обновлён: %s = %s", name, field, value);
        }

        return "⚠️ Не удалось обновить хакатон. Проверьте имя и поле.";
    }

    @Transactional
    public String updateTask(Map<String, Object> data) {
        String title = str(data, "title");
        String field = str(data, "field");
        String value = str(data, "value");

        if (title == null || title.isBlank()) {
            return "❌ Не указана задача для обновления.";
        }
        if (field == null || field.isBlank()) {
            return "❌ Не указано поле для обновления. Пример: \"обнови статус задачи Документация на DONE\"";
        }
        if (value == null || value.isBlank()) {
            return "❌ Не указано новое значение.";
        }

        Optional<Task> opt = StreamSupport.stream(taskRepo.findAll().spliterator(), false)
                .filter(t -> t.getTitle() != null && t.getTitle().equalsIgnoreCase(title))
                .findFirst();

        if (opt.isEmpty()) {
            return "❌ Задача '" + title + "' не найдена.";
        }

        Task t = opt.get();
        boolean updated = false;

        switch (field.toLowerCase()) {
            case "status" -> {
                t.setStatus(value.toUpperCase());
                if ("DONE".equalsIgnoreCase(value)) {
                    t.setCompletedAt(LocalDateTime.now());
                }
                updated = true;
            }
            case "assignee" -> {
                t.setAssignee(value);
                updated = true;
            }
            case "deadline" -> {
                LocalDate d = parseDate(Collections.singletonMap("deadline", value));
                if (d != null) {
                    t.setDeadline(d);
                    updated = true;
                }
            }
            case "notes" -> {
                t.setNotes(value);
                updated = true;
            }
            default -> log.warn("Unknown field for task update: {}", field);
        }

        if (updated) {
            taskRepo.save(t);
            return String.format("✅ Задача '%s' обновлена: %s = %s", title, field, value);
        }

        return "⚠️ Не удалось обновить задачу. Проверьте имя и поле.";
    }

    @Transactional
    public String deleteHackathon(Map<String, Object> data) {
        String name = str(data, "name");
        if (name == null || name.isBlank()) {
            return "❌ Не указано название хакатона для удаления.";
        }

        Optional<Hackathon> opt = hackathonRepo.findAllOrdered().stream()
                .filter(h -> h.getName() != null && h.getName().equalsIgnoreCase(name))
                .findFirst();

        if (opt.isEmpty()) {
            return "❌ Хакатон '" + name + "' не найден.";
        }

        hackathonRepo.delete(opt.get());
        log.info("Hackathon deleted via AI: {}", name);
        return "🗑️ Хакатон '" + name + "' удалён.";
    }

    @Transactional
    public String deleteTask(Map<String, Object> data) {
        String title = str(data, "title");
        if (title == null || title.isBlank()) {
            return "❌ Не указана задача для удаления.";
        }

        Optional<Task> opt = StreamSupport.stream(taskRepo.findAll().spliterator(), false)
                .filter(t -> t.getTitle() != null && t.getTitle().equalsIgnoreCase(title))
                .findFirst();

        if (opt.isEmpty()) {
            return "❌ Задача '" + title + "' не найдена.";
        }

        taskRepo.delete(opt.get());
        log.info("Task deleted via AI: {}", title);
        return "🗑️ Задача '" + title + "' удалена.";
    }

    @Transactional
    public String createReminder(Map<String, Object> data) {
        String text = str(data, "text");
        String scheduledAtStr = str(data, "scheduledAt");

        if (text == null || text.isBlank()) {
            return "❌ Не указано текст напоминания.";
        }

        Reminder reminder = new Reminder();
        reminder.setText(text);
        reminder.setType("MANUAL");
        reminder.setExecuted(false);

        if (scheduledAtStr != null && !scheduledAtStr.isBlank()) {
            try {
                reminder.setScheduledAt(LocalDateTime.parse(scheduledAtStr,
                        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            } catch (Exception e) {
                log.warn("Failed to parse scheduledAt: {}", scheduledAtStr);
            }
        }

        reminderRepo.save(reminder);
        log.info("Reminder created via AI: {}", text);
        return "⏰ Напоминание '" + text + "' создано";
    }

    // ==================== УТИЛИТЫ ====================

    private Map<String, Object> parseJsonResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replace("```json", "").replace("```", "").trim();
        }
        return objectMapper.readValue(cleaned, new TypeReference<>() {});
    }

    private LocalDate parseDate(Map<String, Object> data, String key) {
        String value = str(data, key);
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse date '{}': {}", value, e.getMessage());
            return null;
        }
    }

    private LocalDate parseDate(Map<String, Object> data) {
        return parseDate(data, "date");
    }

    private Long resolveHackathonId(String hackathonName) {
        if (hackathonName == null || hackathonName.isBlank()) {
            List<Hackathon> all = hackathonRepo.findAllOrdered();
            return all.isEmpty() ? null : all.getFirst().getId();
        }

        return hackathonRepo.findAllOrdered().stream()
                .filter(h -> h.getName() != null && h.getName().equalsIgnoreCase(hackathonName))
                .findFirst()
                .map(Hackathon::getId)
                .orElse(null);
    }

    private String str(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null) return null;
        return val.toString().trim();
    }
}
