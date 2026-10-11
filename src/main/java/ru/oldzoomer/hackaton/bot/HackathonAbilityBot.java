package ru.oldzoomer.hackaton.bot;

import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.abilitybots.api.bot.AbilityBot;
import org.telegram.telegrambots.abilitybots.api.objects.Ability;
import org.telegram.telegrambots.abilitybots.api.objects.Locality;

import org.telegram.telegrambots.abilitybots.api.objects.Privacy;
import org.telegram.telegrambots.abilitybots.api.objects.Reply;
import org.telegram.telegrambots.abilitybots.api.sender.SilentSender;
import org.telegram.telegrambots.abilitybots.api.util.AbilityExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.oldzoomer.hackaton.config.BotConfig;
import ru.oldzoomer.hackaton.entity.Hackathon;
import ru.oldzoomer.hackaton.entity.Reminder;
import ru.oldzoomer.hackaton.entity.Task;
import ru.oldzoomer.hackaton.entity.User;
import ru.oldzoomer.hackaton.repository.ReminderRepository;
import ru.oldzoomer.hackaton.repository.UserRepository;
import ru.oldzoomer.hackaton.service.HackathonService;
import ru.oldzoomer.hackaton.service.ReminderService;
import ru.oldzoomer.hackaton.service.TaskService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.telegram.telegrambots.abilitybots.api.objects.Ability.builder;

/**
 * Telegram bot implementing the abilities framework for hackathon management.
 * Commands: /start, /help, /hackathons, /tasks, /reminders
 */
@Component
@Log4j2
public class HackathonAbilityBot extends AbilityBot implements AbilityExtension {

    private final HackathonService hackathonService;
    private final TaskService taskService;
    private final ReminderService reminderService;
    private final UserRepository userRepository;
    private final ReminderRepository reminderRepository;
    private final BotConfig botConfig;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM", Locale.of("RU"));
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("RU"));

    public HackathonAbilityBot(TelegramClient client, BotConfig botConfig,
                               HackathonService hackathonService,
                               TaskService taskService,
                               ReminderService reminderService,
                               UserRepository userRepository,
                               ReminderRepository reminderRepository) {
        super(client, botConfig.getUsername());
        this.hackathonService = hackathonService;
        this.taskService = taskService;
        this.reminderService = reminderService;
        this.userRepository = userRepository;
        this.reminderRepository = reminderRepository;
        this.botConfig = botConfig;
    }

    @Override
    public long creatorId() {
        return botConfig.getCreatorId();
    }

    @Override
    public void consume(Update update) {
        super.consume(update);
    }

    // ==================== ABILITIES ====================

    public Ability startAbility() {
        return builder()
                .name("start")
                .info("Welcome message and main menu")
                .locality(Locality.ALL)
.privacy(Privacy.PUBLIC)
                .action(m -> {
                    long chatId = m.chatId();
                    SilentSender silent = getSilent();

                    // Register user in DB (Issue 6)
                    var telegramUser = m.user();
                    if (telegramUser != null) {
                        userRepository.findByTelegramId(telegramUser.getId())
                                .orElseGet(() -> {
                                    User user = new User(
                                            telegramUser.getId(),
                                            telegramUser.getUserName(),
                                            telegramUser.getFirstName(),
                                            telegramUser.getLastName()
                                    );
                                    userRepository.save(user);
                                    return user;
                                });
                    }

                    String sb = """
                            👋 _Добро пожаловать в бота хакатонов!_
                            
                            Я помогу вам следить за хакатонами, задачами и напоминаниями.
                            
                            Доступные команды:
                            /hackathons — список активных хакатонов
                            /tasks — ваши задачи
                            /reminders — напоминания
                            /help — справка
                            """;

                    silent.sendMd(sb, chatId);
                })
                .build();
    }

    public Ability helpAbility() {
        return builder()
                .name("help")
                .info("Show help and available commands")
                .locality(Locality.ALL)
.privacy(Privacy.PUBLIC)
                .action(m -> {
                    long chatId = m.chatId();
                    SilentSender silent = getSilent();
                    String sb = """
                            📖 _Справочник команд:_
                            
                            🔹 /start — приветствие и главное меню
                            🔹 /help — эта справка
                            🔹 /hackathons — список активных и предстоящих хакатонов
                            🔹 /tasks — задачи: в работе и просроченные
                            🔹 /reminders — предстоящие напоминания
                            """;
                    silent.sendMd(sb, chatId);
                })
                .build();
    }

    public Ability hackathonsAbility() {
        return builder()
                .name("hackathons")
                .info("List active hackathons")
.locality(Locality.USER)
                .privacy(Privacy.PUBLIC)
                .action(m -> {
                    long chatId = m.chatId();
                    SilentSender silent = getSilent();

                    List<Hackathon> hackathons = hackathonService.findAllActive();
                    if (hackathons.isEmpty()) {
                        silent.sendMd("📭 _Нет активных хакатонов._", chatId);
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("🏆 _АКТИВНЫЕ ХАКАТОНЫ:_\n\n");

                    AtomicInteger idx = new AtomicInteger(1);
                    for (Hackathon h : hackathons) {
                        sb.append(idx.getAndIncrement()).append(". _").append(h.getName()).append("_\n");
                        if (h.getTrack() != null) {
                            sb.append("   📌 Трек: ").append(h.getTrack()).append("\n");
                        }
                        sb.append("   ").append(hackathonService.getDaysUntil(h.getRegistrationDeadline())).append(" до регистрации\n");
                        if (h.getDescription() != null) {
                            sb.append("   ").append(h.getDescription()).append("\n");
                        }
                        sb.append("\n");
                    }
                    silent.sendMd(sb.toString(), chatId);
                })
                .build();
    }

    public Ability tasksAbility() {
        return builder()
                .name("tasks")
                .info("List in-progress and overdue tasks")
.locality(Locality.USER)
                .privacy(Privacy.PUBLIC)
                .action(m -> {
                    long chatId = m.chatId();
                    SilentSender silent = getSilent();

                    List<Task> inProgress = taskService.findInProgress();
                    List<Task> overdue = taskService.findOverdue();

                    StringBuilder sb = new StringBuilder();
                    sb.append("📋 _ЗАДАЧИ:_\n\n");

                    if (!inProgress.isEmpty()) {
                        sb.append("🔄 _В работе:_\n");
                        for (Task t : inProgress) {
                            sb.append("  🏃 ").append(t.getTitle());
                            if (t.getAssignee() != null) {
                                sb.append(" — ").append(t.getAssignee());
                            }
                            sb.append("\n");
                        }
                        sb.append("\n");
                    }

                    if (!overdue.isEmpty()) {
                        sb.append("⛔ _Просроченные:_\n");
                        for (Task t : overdue) {
                            sb.append("  ⚠️ ").append(t.getTitle());
                            if (t.getDeadline() != null) {
                                sb.append(" (до ").append(t.getDeadline().format(DATE_FORMATTER)).append(")");
                            }
                            sb.append("\n");
                        }
                        sb.append("\n");
                    }

                    if (inProgress.isEmpty() && overdue.isEmpty()) {
                        sb.append("✅ Нет активных или просроченных задач!\n");
                    }

                    silent.sendMd(sb.toString(), chatId);
                })
                .build();
    }

    public Ability remindersAbility() {
        return builder()
                .name("reminders")
                .info("List pending reminders")
.locality(Locality.USER)
                .privacy(Privacy.PUBLIC)
                .action(m -> {
                    long chatId = m.chatId();
                    SilentSender silent = getSilent();

                    List<Reminder> reminders = reminderService.findPending();
                    if (reminders.isEmpty()) {
                        silent.sendMd("📭 _Нет предстоящих напоминаний._", chatId);
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("⏰ _НАПОМИНАНИЯ:_\n\n");

                    for (Reminder r : reminders) {
                        sb.append("🔔 ").append(r.getText());
                        if (r.getScheduledAt() != null) {
                            sb.append(" — ").append(r.getScheduledAt().toString(), 0, 16);
                        }
                        sb.append("\n");
                    }

                    silent.sendMd(sb.toString(), chatId);
                })
                .build();
    }

    // ==================== REPLIES ====================

    /** Handle inline button callbacks */
    public Reply inlineButtonReply() {
        return Reply.of((bot, update) -> {
            if (update.hasCallbackQuery()) {
                var cb = update.getCallbackQuery();
                long chatId = cb.getMessage().getChatId();
                SilentSender silent = bot.getSilent();

                String data = cb.getData();
                switch (data) {
                    case "hackathons" -> {
                        List<Hackathon> hackathons = hackathonService.findAllActive();
                        if (hackathons.isEmpty()) {
                            silent.sendMd("📭 _Нет активных хакатонов._", chatId);
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append("🏆 _АКТИВНЫЕ ХАКАТОНЫ:_\n\n");
                        AtomicInteger idx = new AtomicInteger(1);
                        for (Hackathon h : hackathons) {
                            sb.append(idx.getAndIncrement()).append(". _").append(h.getName()).append("_\n");
                            if (h.getTrack() != null) {
                                sb.append("   📌 Трек: ").append(h.getTrack()).append("\n");
                            }
                            sb.append("   ").append(hackathonService.getDaysUntil(h.getRegistrationDeadline())).append(" до регистрации\n");
                            sb.append("\n");
                        }
                        silent.sendMd(sb.toString(), chatId);
                    }
                    case "tasks" -> {
                        List<Task> inProgress = taskService.findInProgress();
                        List<Task> overdue = taskService.findOverdue();
                        StringBuilder sb = new StringBuilder();
                        sb.append("📋 _ЗАДАЧИ:_\n\n");
                        if (!inProgress.isEmpty()) {
                            sb.append("🔄 _В работе:_\n");
                            inProgress.forEach(t -> sb.append("  🏃 ").append(t.getTitle()).append("\n"));
                            sb.append("\n");
                        }
                        if (!overdue.isEmpty()) {
                            sb.append("⛔ _Просроченные:_\n");
                            overdue.forEach(t -> sb.append("  ⚠️ ").append(t.getTitle()).append("\n"));
                            sb.append("\n");
                        }
                        if (inProgress.isEmpty() && overdue.isEmpty()) {
                            sb.append("✅ Нет активных задач!\n");
                        }
                        silent.sendMd(sb.toString(), chatId);
                    }
                    case "reminders" -> {
                        List<Reminder> pending = reminderService.findPending();
                        if (pending.isEmpty()) {
                            silent.sendMd("📭 _Нет напоминаний._", chatId);
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append("⏰ _НАПОМИНАНИЯ:_\n\n");
                        pending.forEach(r -> sb.append("🔔 ").append(r.getText()).append("\n"));
                        silent.sendMd(sb.toString(), chatId);
                    }
                    case null, default -> silent.sendMd("❓ _Неизвестная команда._", chatId);
                }

                // Answer callback to remove spinner
                bot.getSilent().execute(
                        AnswerCallbackQuery.builder()
                                .callbackQueryId(cb.getId())
                                .build()
                );
            }
        }, Update::hasCallbackQuery);
    }

    /** Run every day at 10:00 Moscow time — send daily summary to all users */
    @Scheduled(cron = "0 0 10 * * *")
    public void sendDailySummaries() {
        log.info("Running daily summaries at 10:00 MSK");
        List<User> users = userRepository.findRecentUsers();
        if (users.isEmpty()) {
            log.info("No users found, skipping daily summaries");
            return;
        }

        String summary = buildDailySummary();
        for (User user : users) {
            try {
                sendMessage(user.getTelegramId(), summary);

                // Create a reminder record
                Reminder r = new Reminder();
                r.setTelegramUserId(user.getTelegramId());
                r.setScheduledAt(LocalDateTime.now());
                r.setType("DAILY_SUMMARY");
                r.setText("📊 Ежедневная сводка");
                reminderRepository.save(r);
            } catch (Exception e) {
                log.error("Failed to send daily summary to user {}", maskChatId(user.getTelegramId()), e);
            }
        }
    }

    /** Check for pending reminders every 5 minutes */
    @Scheduled(cron = "0 */5 * * * *")
    public void checkPendingReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> pending = reminderRepository.findPendingReminders(now);

        for (Reminder reminder : pending) {
            try {
                if (!reminder.isExecuted()) {
                    sendMessage(reminder.getTelegramUserId(), "⏰ " + reminder.getText());
                    reminderRepository.markExecuted(reminder.getId());
                    log.info("Sent reminder {} to user {}", reminder.getId(), reminder.getTelegramUserId());
                }
            } catch (Exception e) {
                log.error("Failed to send reminder {}", reminder.getId(), e);
            }
        }

        // Cleanup old executed reminders
        reminderRepository.cleanupOldExecuted(LocalDateTime.now().minusDays(30));
    }

    /** Check for overdue tasks and ping daily */
    @Scheduled(cron = "0 30 10 * * *")
    public void checkOverdueTasks() {
        log.info("Checking overdue tasks");
        List<Task> overdue = taskService.findOverdue();
        if (overdue.isEmpty()) return;

        List<User> users = userRepository.findRecentUsers();
        for (User user : users) {
            for (Task task : overdue) {
                // Only send to the user assigned to the task (Issue 2)
                if (task.getAssignee() == null || !task.getAssignee().equals(user.getUsername())) {
                    continue;
                }
                try {
                    String msg = String.format(
                            "⛔ _Просрочена задача!_\n\n📌 %s\n📅 Было нужно: %s\n\n_Планируешь сделать или перенести дедлайн?_",
                            task.getTitle(),
                            task.getDeadline().format(DATE_TIME_FORMATTER)
                    );
                    sendMessage(user.getTelegramId(), msg);
                } catch (Exception e) {
                    log.error("Failed to send overdue notification", e);
                }
            }
        }
    }

    // ==================== HELPERS ====================

    private String buildDailySummary() {
        List<Hackathon> hackathons = hackathonService.findAllActive();
        LocalDate today = LocalDate.now();

        StringBuilder sb = new StringBuilder();
        sb.append("📊 _СВОДКА НА СЕГОДНЯ:_ _").append(today.format(DATE_TIME_FORMATTER)).append("_\n\n");

        // Upcoming deadlines (next 7 days)
        List<Hackathon> upcoming = hackathons.stream()
                .filter(h -> h.getRegistrationDeadline() != null &&
                        !h.getRegistrationDeadline().isBefore(today) &&
                        h.getRegistrationDeadline().isAfter(today.minusDays(1)) &&
                        !h.getRegistrationDeadline().isAfter(today.plusDays(7)))
                .sorted(Comparator.comparing(Hackathon::getRegistrationDeadline))
                .toList();

        if (!upcoming.isEmpty()) {
            sb.append("📌 _Ближайшие дедлайны:_\n");
            upcoming.forEach(h -> {
                long days = ChronoUnit.DAYS.between(today, h.getRegistrationDeadline());
                String emoji = days <= 1 ? "🔥" : "🔹";
                sb.append("  ").append(emoji).append(" ").append(h.getName())
                        .append(" — ").append(h.getRegistrationDeadline().format(DATE_FORMATTER))
                        .append(" (").append(days).append(" дн.)\n");
            });
            sb.append("\n");
        }

        // Overdue tasks
        List<Task> overdueTasks = taskService.findOverdue();
        if (!overdueTasks.isEmpty()) {
            sb.append("⛔ _Просроченные задачи:_\n");
            overdueTasks.stream().limit(5).forEach(t ->
                    sb.append("  ⚠️ ").append(t.getTitle()).append("\n"));
            sb.append("\n");
        }

        // In progress
        List<Task> inProgress = taskService.findInProgress();
        if (!inProgress.isEmpty()) {
            sb.append("🔄 _В работе:_\n");
            inProgress.stream().limit(5).forEach(t -> sb.append("  🏃 ").append(t.getTitle()).append("\n"));
            sb.append("\n");
        }

        // Plan
        sb.append("🎯 _План:_\n");
        if (!inProgress.isEmpty()) {
            inProgress.stream().limit(3).forEach(t -> sb.append("  • Продолжить: ").append(t.getTitle()).append("\n"));
        } else if (!overdueTasks.isEmpty()) {
            overdueTasks.stream().limit(3).forEach(t -> sb.append("  • Закрыть: ").append(t.getTitle()).append("\n"));
        } else {
            sb.append("  • Нет просроченных задач. Отличная работа! 🎉\n");
        }

        return sb.toString();
    }

    private void sendMessage(Long chatId, String text) {
        try {
            getSilent().sendMd(text, chatId);
            log.info("Sent message to Telegram user {}", maskChatId(chatId));
        } catch (Exception e) {
            log.error("Failed to send message to Telegram user {}", maskChatId(chatId), e);
        }
    }

    /** Mask PII: show only last 4 digits of Telegram chat ID */
    private String maskChatId(Long chatId) {
        String id = String.valueOf(chatId);
        if (id.length() <= 4) return "****";
        return "****" + id.substring(id.length() - 4);
    }
}
