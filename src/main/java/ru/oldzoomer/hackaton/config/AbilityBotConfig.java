package ru.oldzoomer.hackaton.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.abilitybots.api.bot.AbilityBot;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.oldzoomer.hackaton.bot.HackathonAbilityBot;

@Configuration
@Log4j2
@RequiredArgsConstructor
public class AbilityBotConfig {
    private final HackathonAbilityBot hackathonAbilityBot;
    private final BotConfig botConfig;
    private final TelegramBotsLongPollingApplication botsApplication;

    /**
     * Initialize the AbilityBot - register abilities and ensure onRegister is called.
     * This is required for telegrambots-abilities 7.2.x+ where onRegister()
     * is not called automatically in Spring Boot context.
     */
    @Bean
    public AbilityBot abilityBot() {
        // Force registration of abilities - workaround for telegrambots-abilities bug
        hackathonAbilityBot.onRegister();
        return hackathonAbilityBot;
    }

    /**
     * Manually register the AbilityBot with the long polling application.
     * This is necessary because @EnableBot only works with TelegramLongPollingBot subclasses.
     */
    @PostConstruct
    public void registerBot() throws TelegramApiException {
        log.info("Registering AbilityBot: {}", botConfig.getUsername());
        botsApplication.registerBot(botConfig.getToken(), hackathonAbilityBot);
        log.info("AbilityBot registered successfully");
    }
}
