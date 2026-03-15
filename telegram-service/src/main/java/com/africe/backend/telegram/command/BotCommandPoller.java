package com.africe.backend.telegram.command;

import com.africe.backend.telegram.client.TelegramClient;
import com.africe.backend.telegram.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotCommandPoller {

    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final CreateAdminCommand createAdminCommand;

    private long lastUpdateId = 0;

    @Scheduled(fixedDelay = 3000)
    public void pollForCommands() {
        if (telegramProperties.getToken() == null || telegramProperties.getToken().isBlank()) {
            return;
        }

        JsonNode response = telegramClient.getUpdates(lastUpdateId + 1);
        if (response == null || !response.has("result")) {
            return;
        }

        JsonNode results = response.get("result");
        for (JsonNode update : results) {
            lastUpdateId = update.get("update_id").asLong();

            JsonNode message = update.get("message");
            if (message == null || !message.has("text")) {
                continue;
            }

            String text = message.get("text").asText();
            String chatId = String.valueOf(message.get("chat").get("id").asLong());

            if (text.startsWith("/createadmin")) {
                createAdminCommand.execute(chatId, text);
            } else if (text.startsWith("/help")) {
                handleHelp(chatId);
            }
        }
    }

    private void handleHelp(String chatId) {
        String helpMessage = """
                <b>Команди бота Africe</b>

                /createadmin email@example.com [ім'я]
                Створити новий обліковий запис адміністратора

                /help
                Показати це повідомлення
                """;
        telegramClient.sendMessage(chatId, helpMessage);
    }
}
