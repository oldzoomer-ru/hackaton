package ru.oldzoomer.hackaton.bot;

import lombok.extern.log4j.Log4j2;
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
import ru.oldzoomer.hackaton.entity.Hackathon;
import ru.oldzoomer.hackaton.entity.Reminder;
import ru.oldzoomer.hackaton.entity.Task;
import ru.oldzoomer.hackaton.service.HackathonService;
import ru.oldzoomer.hackaton.service.ReminderService;
import ru.oldzoomer.hackaton.service.TaskService;

import java.time.format.DateTimeFormatter;
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

    public HackathonAbilityBot(TelegramClient client, String botUsername,
                               HackathonService hackathonService,
                               TaskService taskService,
                               ReminderService reminderService) {
        super(client, botUsername);
        this.hackathonService = hackathonService;
        this.taskService = taskService;
        this.reminderService = reminderService;
    }

    @Override
    public long creatorId() {
        return 0L;
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
                    String sb = """
                            👋 _Добро пожаловать в бота хакинатонов!_
                            
                            Я помогу вам следить за хакинатонами, задачами и напоминаниями.
                            
                            Доступные команды:
                            /hackathons — список активных хакаулонов
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
                            🔹 /hackathons — список активных и предстоящих хакаулонов
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
                .locality(Locality.ALL)
                .privacy(Privacy.PUBLIC)
                .action(m -> {
                    long chatId = m.chatId();
                    SilentSender silent = getSilent();

                    List<Hackathon> hackathons = hackathonService.findAllActive();
                    if (hackathons.isEmpty()) {
                        silent.sendMd("📭 _Нет активных хакаулонов._", chatId);
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
                .locality(Locality.ALL)
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
                                sb.append(" (до ").append(t.getDeadline().format(DateTimeFormatter.ofPattern("dd.MM", Locale.of("RU")))).append(")");
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
                .locality(Locality.ALL)
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
                if ("hackathons".equals(data)) {
                    List<Hackathon> hackathons = hackathonService.findAllActive();
                    if (hackathons.isEmpty()) {
                        silent.sendMd("📭 _Нет активных хакаулонов._", chatId);
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
                } else if ("tasks".equals(data)) {
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
                } else if ("reminders".equals(data)) {
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

                // Answer callback to remove spinner
                bot.getSilent().execute(
                        AnswerCallbackQuery.builder()
                                .callbackQueryId(cb.getId())
                                .build()
                );
            }
        });
    }
}
