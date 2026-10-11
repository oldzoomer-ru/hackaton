package ru.oldzoomer.hackaton.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring AI ChatClient.
 * Uses OpenAI-compatible API (Cloud.ru Foundation Models).
 */
@Configuration
public class AiConfig {
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("Ты — ассистент для управления хакатонами. Ты помогаешь создавать и редактировать сущности: хакатоны, задачи и напоминания. Отвечай кратко и по делу.")
                .build();
    }
}
