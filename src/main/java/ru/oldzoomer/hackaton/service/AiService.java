package ru.oldzoomer.hackaton.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oldzoomer.hackaton.entity.Hackathon;
import ru.oldzoomer.hackaton.entity.Task;
import ru.oldzoomer.hackaton.repository.HackathonRepository;
import ru.oldzoomer.hackaton.repository.TaskRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service that uses Spring AI to parse natural language into entities
 * and persist them via existing repositories.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class AiService {

    private final ChatClient chatClient;
    private final TaskService taskService;
    private final HackathonRepository hackathonRepo;
    private final TaskRepository taskRepo;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final String SYSTEM_PROMPT = """
            Ты — ассистент для управления хакатонами. Твоя задача — извлекать данные из сообщений
            пользователя и создавать сущности в базе данных.

            Ты работаешь в двух режимах:

            РЕЖИМ 1: Создание хакатона
            Извлеки: name (обязательно), track, registrationDeadline, onlineStageStart, onlineStageEnd, resultsDate, finalStageStart, finalStageEnd, description
            По умолчанию: status = "REGISTERING"

            РЕЖИМ 2: Создание задачи
            Извлеки: title (обязательно), assignee, deadline, notes, hackathonName
            По умолчанию: status = "TODO"

            РЕЖИМ 3: Уточнение
            Если ключевые данные отсутствуют — задай уточняющий вопрос.

            ВАЖНО: Отвечай ТОЛЬКО JSON в следующем формате:
            {"action": "create_hackathon" | "create_task" | "ask_question", ...}

            Для create_hackathon:
            {"action": "create_hackathon", "name": "...", "track": "...", "registrationDeadline": "dd.mm.yyyy", "onlineStageStart": "dd.mm.yyyy", "onlineStageEnd": "dd.mm.yyyy", "resultsDate": "dd.mm.yyyy", "finalStageStart": "dd.mm.yyyy", "finalStageEnd": "dd.mm.yyyy", "description": "..."}

            Для create_task:
            {"action": "create_task", "title": "...", "assignee": "...", "deadline": "dd.mm.yyyy", "notes": "...", "hackathonName": "..."}

            Для ask_question:
            {"action": "ask_question", "question": "..."}

            Правила:
            - Если дата в формате "через N дней" — вычисли дату относительно сегодня
            - Если дата "15 января" — интерпретируй как текущий год
            - Если дата "дд.мм" — интерпретируй как текущий год
            - Если пользователь не указал название хакатона при создании задачи — найди последний созданный хакатон
            - Отвечай ТОЛЬКО валидным JSON, без дополнительного текста
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Process natural language input: AI extracts data, creates entities, returns confirmation.
     *
     * @param userInput natural language message from user
     * @return confirmation message or clarification question
     */
    public String processNaturalLanguage(String userInput) {
        log.info("Processing natural language input: {}", userInput);

        try {
            // Step 1: Ask AI to extract structured data
            String aiResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userInput)
                    .call()
                    .content();

            log.info("AI raw response: {}", aiResponse);

            if (aiResponse == null) {
                throw new RuntimeException("AI response is empty");
            }

            // Step 2: Parse JSON from AI response
            Map<String, Object> data = parseJsonResponse(aiResponse);

            if (data == null) {
                log.warn("Could not parse AI response as JSON: {}", aiResponse);
                return "❌ Не удалось обработать ответ. Попробуйте переформулировать запрос.";
            }

            String action = (String) data.get("action");

            // Step 3: Execute based on action
            return switch (Objects.requireNonNull(action)) {
                case "create_hackathon" -> createHackathon(data);
                case "create_task" -> createTask(data);
                case "ask_question" -> (String) data.getOrDefault("question", "Уточните ваш запрос.");
                default -> "❓ Неизвестное действие. Попробуйте переформулировать.";
            };

        } catch (Exception e) {
            log.error("Error processing natural language: {}", userInput, e);
            return "❌ Ошибка при обработке запроса: " + e.getMessage();
        }
    }

    /**
     * Parse JSON response from AI, stripping markdown code blocks if present.
     */
    private Map<String, Object> parseJsonResponse(String response) {
        try {
            // Strip markdown code blocks if present
            String cleaned = response.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replace("```json", "").replace("```", "").trim();
            }
            return objectMapper.readValue(cleaned, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("JSON parsing failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create a hackathon from extracted AI data.
     */
    @Transactional
    public String createHackathon(Map<String, Object> data) {
        String name = (String) data.get("name");
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

    /**
     * Create a task from extracted AI data.
     */
    @Transactional
    public String createTask(Map<String, Object> data) {
        String title = (String) data.get("title");
        if (title == null || title.isBlank()) {
            return "❌ Не указана задача.";
        }

        String hackathonName = str(data, "hackathonName");
        Long hackathonId = resolveHackathonId(hackathonName);

        if (hackathonId == null) {
            return "❌ Хакатон '" + (hackathonName != null ? hackathonName : "<не указан>")
                    + "' не найден. Сначала создайте хакатон.";
        }

        String assignee = str(data, "assignee");
        LocalDate deadline = parseDate(data, "deadline");
        String notes = str(data, "notes");

        Task task = taskService.addTask(hackathonId, title, assignee, deadline, notes);

        String assigneeStr = task.getAssignee() != null ? ", Исполнитель: " + task.getAssignee() : "";
        String deadlineStr = task.getDeadline() != null ? ", дедлайн: " + task.getDeadline().format(DATE_FORMATTER) : "";

        String msg = String.format("✅ Задача '%s' добавлена в хакатон '%s'.%s%s",
                task.getTitle(), hackathonName, assigneeStr, deadlineStr);
        log.info("Task created via AI: {} for hackathon {}", title, hackathonId);
        return msg;
    }

    /**
     * Resolve hackathon ID by name. Returns null if not found.
     */
    private Long resolveHackathonId(String hackathonName) {
        if (hackathonName == null || hackathonName.isBlank()) {
            // Fallback: use the most recent hackathon
            List<Hackathon> all = hackathonRepo.findAllOrdered();
            if (!all.isEmpty()) {
                return all.getFirst().getId();
            }
            return null;
        }

        List<Hackathon> all = hackathonRepo.findAllOrdered();
        return all.stream()
                .filter(h -> h.getName() != null && h.getName().equalsIgnoreCase(hackathonName))
                .findFirst()
                .map(Hackathon::getId)
                .orElse(null);
    }

    /**
     * Parse a date string from AI data.
     */
    private LocalDate parseDate(Map<String, Object> data, String key) {
        String value = str(data, key);
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse date '{}': {}", value, e.getMessage());
            return null;
        }
    }

    /**
     * Safely extract a string from map data.
     */
    private String str(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null) return null;
        return val.toString().trim();
    }
}
