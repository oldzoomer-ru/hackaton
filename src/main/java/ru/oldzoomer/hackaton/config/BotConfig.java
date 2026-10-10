package ru.oldzoomer.hackaton.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Telegram Bot.
 * Binds to {@code telegram.bot.*} prefix in application.yaml.
 */
@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
@Getter
@Setter
public class BotConfig {

    /**
     * Bot token obtained from @BotFather.
     */
    private String token;

    /**
     * Bot username obtained from @BotFather (without @ prefix).
     */
    private String username;

    /**
     * HTTP Proxy URL
     */
    private String proxyUrl;

    /**
     * Telegram API endpoint
     */
    private String telegramApiEndpoint;

    /**
     * Creator ID
     */
    private long creatorId;
}
