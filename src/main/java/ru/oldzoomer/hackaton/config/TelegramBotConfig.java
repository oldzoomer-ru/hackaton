package ru.oldzoomer.hackaton.config;

import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.abilitybots.api.sender.SilentSender;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.TelegramOkHttpClientFactory;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.generics.TelegramClient;

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
public class TelegramBotConfig {

    private final BotConfig botConfig;

    @Autowired
    public TelegramBotConfig(BotConfig botConfig) {
        this.botConfig = botConfig;
    }

    /**
     * Telegram client bean using OkHttp implementation.
     */
    @Bean
    public TelegramClient telegramClient() {
        if (botConfig.getTelegramApiEndpoint() != null && !botConfig.getTelegramApiEndpoint().isBlank()) {
            URI telegramUri = URI.create(botConfig.getTelegramApiEndpoint());
            return new OkHttpTelegramClient(botConfig.getToken(), new TelegramUrl(telegramUri.getScheme(),
                    telegramUri.getHost(), telegramUri.getPort() != -1 ? telegramUri.getPort() : 443, false));
        } else if (botConfig.getProxyUrl() != null && !botConfig.getProxyUrl().isBlank()) {
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
        } else return new OkHttpTelegramClient(botConfig.getToken());
    }

    /**
     * SilentSender for asynchronous bot operations.
     */
    @Bean
    public SilentSender silentSender(TelegramClient telegramClient) {
        return new SilentSender(telegramClient);
    }
}
