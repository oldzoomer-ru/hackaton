package ru.oldzoomer.hackaton.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.abilitybots.api.bot.AbilityBot;
import org.telegram.telegrambots.abilitybots.api.sender.SilentSender;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.TelegramOkHttpClientFactory;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.oldzoomer.hackaton.bot.HackathonAbilityBot;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

/**
 * Configuration for Telegram Bot with Abilities framework.
 * Manually registers the AbilityBot with the Spring Boot LongPolling application.
 *
 * <p>Note: @EnableBot from telegrambots-springboot-longpolling-starter only works
 * with TelegramLongPollingBot subclasses, not AbilityBot.
 * So we register the bot manually here using TelegramBotsLongPollingApplication.
 */
@Configuration
@Log4j2
@Profile("!test")
public class TelegramBotConfig {

    private final BotConfig botConfig;
    private final HackathonAbilityBot hackathonAbilityBot;
    private final TelegramBotsLongPollingApplication botsApplication;

    @Autowired
    public TelegramBotConfig(BotConfig botConfig,
                             HackathonAbilityBot hackathonAbilityBot,
                             TelegramBotsLongPollingApplication botsApplication) {
        this.botConfig = botConfig;
        this.hackathonAbilityBot = hackathonAbilityBot;
        this.botsApplication = botsApplication;
    }

    /**
     * Telegram client bean using OkHttp implementation.
     */
    @Bean
    public TelegramClient telegramClient() {
        if (botConfig.getProxyUrl() == null) return new OkHttpTelegramClient(botConfig.getToken());
        else {
            URI proxyUri = URI.create(botConfig.getProxyUrl());
            String[] userInfo = proxyUri.getUserInfo().split(":", 2);
            String credential = Credentials.basic(userInfo[0], userInfo[1]);
            OkHttpClient okHttpClient;
            if (proxyUri.getScheme().equalsIgnoreCase("http") || proxyUri.getScheme().equalsIgnoreCase("https")) {
                okHttpClient = new TelegramOkHttpClientFactory.HttpProxyOkHttpClientCreator(
                        () -> new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())),
                        () -> new Authenticator() {
                            @Override
                            public @NonNull Request authenticate(@Nullable Route route, @NonNull Response response) {
                                return response.request().newBuilder()
                                        .header("Proxy-Authorization", credential)
                                        .build();
                            }
                        }
                ).get();
            } else {
                okHttpClient = new TelegramOkHttpClientFactory.SocksProxyOkHttpClientCreator(
                        () -> new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort()))
                ).get();
            }

            return new OkHttpTelegramClient(okHttpClient, botConfig.getToken());
        }
    }

    /**
     * SilentSender for asynchronous bot operations.
     */
    @Bean
    public SilentSender silentSender(TelegramClient telegramClient) {
        return new SilentSender(telegramClient);
    }

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
