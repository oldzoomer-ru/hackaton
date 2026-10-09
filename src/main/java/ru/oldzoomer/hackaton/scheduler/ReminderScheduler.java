package ru.oldzoomer.hackaton.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import ru.oldzoomer.hackaton.entity.Hackathon;
import ru.oldzoomer.hackaton.entity.Reminder;
import ru.oldzoomer.hackaton.entity.Task;
import ru.oldzoomer.hackaton.entity.User;
import ru.oldzoomer.hackaton.repository.UserRepository;
import ru.oldzoomer.hackaton.repository.ReminderRepository;
import ru.oldzoomer.hackaton.service.HackathonService;
import ru.oldzoomer.hackaton.service.TaskService;
import ru.oldzoomer.hackaton.bot.HackathonAbilityBot;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Comparator;

@Component
@Log4j2
@RequiredArgsConstructor
@Profile("!test")
public class ReminderScheduler {

    private final HackathonService hackathonService;
    private final TaskService taskService;
    private final UserRepository userRepository;
    private final ReminderRepository reminderRepository;
    private final HackathonAbilityBot abilityBot;

    /** Run every day at 10:00 Moscow time — send daily summary to all users */
    @Scheduled(cron = "0 0 10 * * *", zone = "Europe/Moscow")
    public void sendDailySummaries() {
        log.info("Running daily summaries at 10:00 MSK");
        List<User> users = userRepository.findRecentUsers();
        if (users.isEmpty()) {
            log.info("No users found, skipping daily summaries");
            return;
        }

        for (User user : users) {
            try {
                String summary = buildDailySummary();
                sendMessage(user.getTelegramId(), summary);

                // Create a reminder record
                Reminder r = new Reminder();
                r.setTelegramUserId(user.getId());
                r.setScheduledAt(LocalDateTime.now());
                r.setType("DAILY_SUMMARY");
                r.setText("📊 Ежедневная сводка");
                reminderRepository.save(r);
            } catch (Exception e) {
                log.error("Failed to send daily summary to user {}", user.getTelegramId(), e);
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
    @Scheduled(cron = "0 30 10 * * *", zone = "Europe/Moscow")
    public void checkOverdueTasks() {
        log.info("Checking overdue tasks");
        List<Task> overdue = taskService.findOverdue();
        if (overdue.isEmpty()) return;

        List<User> users = userRepository.findRecentUsers();
        for (User user : users) {
            for (Task task : overdue) {
                try {
                    String msg = String.format(
                            "⛔ _Просрочена задача!_\n\n📌 %s\n📅 Было нужно: %s\n\n_Планируешь сделать или перенести дедлайн?_",
                            task.getTitle(),
                            task.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("RU")))
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
        sb.append("📊 _СВОДКА НА СЕГОДНЯ:_ _").append(today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("RU")))).append("_\n\n");

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
                        .append(" — ").append(h.getRegistrationDeadline().format(DateTimeFormatter.ofPattern("dd.MM", Locale.of("RU"))))
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
        if (abilityBot == null) {
            log.warn("Telegram bot not initialized, skipping message to {}: {}", chatId, text);
            return;
        }
        try {
            abilityBot.getSilent().sendMd(text, chatId);
            log.info("Sent message to Telegram user {}", chatId);
        } catch (Exception e) {
            log.error("Failed to send message to Telegram user {}", chatId, e);
        }
    }
}
