package ru.oldzoomer.hackaton.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oldzoomer.hackaton.entity.Reminder;
import ru.oldzoomer.hackaton.repository.ReminderRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepo;
    private final HackathonService hackathonService;

    /** Generate all reminder events for upcoming hackathon deadlines */
    @Transactional
    public List<Reminder> generateDeadlineReminders(Long userId) {
        List<Reminder> reminders = new ArrayList<>();
        LocalDate today = LocalDate.now();
        List<LocalDateTime> triggerTimes = List.of(
                today.plusDays(7).atTime(LocalTime.of(9, 0)),
                today.plusDays(3).atTime(LocalTime.of(9, 0)),
                today.plusDays(1).atTime(LocalTime.of(8, 0)),
                today.atTime(LocalTime.of(8, 0))
        );

        for (var hack : hackathonService.findAllActive()) {
            // Registration deadline reminders
            if (hack.getRegistrationDeadline() != null) {
                long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, hack.getRegistrationDeadline());
                if (daysUntil >= 0 && daysUntil <= 7) {
                    for (var trigger : triggerTimes) {
                        long triggerDays = java.time.temporal.ChronoUnit.DAYS.between(today, trigger.toLocalDate());
                        if (triggerDays == daysUntil) {
                            Reminder r = new Reminder();
                            r.setTelegramUserId(userId);
                            r.setScheduledAt(trigger);
                            String type = switch ((int) daysUntil) {
                                case 7 -> "DEADLINE_7D";
                                case 3 -> "DEADLINE_3D";
                                case 1 -> "DEADLINE_1D";
                                case 0 -> "DEADLINE_TODAY";
                                default -> "DEADLINE_OTHER";
                            };
                            r.setType(type);
                            r.setText("📌 " + hack.getName() + " — дедлайн регистрации через " + daysUntil + " дн.!");
                            reminders.add(r);
                        }
                    }
                }
                // Overdue registration
                if (daysUntil < 0) {
                    Reminder r = new Reminder();
                    r.setTelegramUserId(userId);
                    r.setScheduledAt(today.atTime(LocalTime.of(9, 0)));
                    r.setType("OVERDUE_DAILY");
                    r.setText("⛔ " + hack.getName() + " — дедлайн регистрации просрочен на " + Math.abs((int) daysUntil) + " дн.!");
                    reminders.add(r);
                }
            }

            // Final stage reminders
            if (hack.getFinalStageStart() != null) {
                long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, hack.getFinalStageStart());
                if (daysUntil >= 0 && daysUntil <= 7) {
                    for (var trigger : triggerTimes) {
                        long triggerDays = java.time.temporal.ChronoUnit.DAYS.between(today, trigger.toLocalDate());
                        if (triggerDays == daysUntil) {
                            Reminder r = new Reminder();
                            r.setTelegramUserId(userId);
                            r.setScheduledAt(trigger);
                            String type = switch ((int) daysUntil) {
                                case 7 -> "DEADLINE_7D";
                                case 3 -> "DEADLINE_3D";
                                case 1 -> "DEADLINE_1D";
                                case 0 -> "DEADLINE_TODAY";
                                default -> "DEADLINE_OTHER";
                            };
                            r.setType(type);
                            r.setText("🏆 " + hack.getName() + " — финал через " + daysUntil + " дн.!");
                            reminders.add(r);
                        }
                    }
                }
            }
        }

        // Daily summary reminder at 10:00
        Reminder daily = new Reminder();
        daily.setTelegramUserId(userId);
        daily.setScheduledAt(today.atTime(LocalTime.of(10, 0)));
        daily.setType("DAILY_SUMMARY");
        daily.setText("📊 Ежедневная сводка");
        reminders.add(daily);

        reminderRepo.saveAll(reminders);
        log.info("Generated {} reminders for user {}", reminders.size(), userId);
        return reminders;
    }

    public List<Reminder> findPending() {
        return reminderRepo.findPendingReminders(LocalDateTime.now());
    }

    @Transactional
    public void markExecuted(Long reminderId) {
        reminderRepo.markExecuted(reminderId);
    }

    @Transactional
    public void cleanupOld() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        reminderRepo.cleanupOldExecuted(cutoff);
    }

    /** Create a manual reminder set by the user via /remind command */
    @Transactional
    public Reminder createManualReminder(Long userId, LocalDateTime scheduledAt, String text) {
        Reminder reminder = new Reminder();
        reminder.setTelegramUserId(userId);
        reminder.setScheduledAt(scheduledAt);
        reminder.setType("MANUAL");
        reminder.setText(text);
        Reminder saved = reminderRepo.save(reminder);
        log.info("Manual reminder created: {} at {}", text, scheduledAt);
        return saved;
    }

    public ReminderRepository reminderRepo() {
        return reminderRepo;
    }
}
