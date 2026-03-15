package com.africe.backend.telegram.command;

import com.africe.backend.telegram.client.TelegramClient;
import com.africe.backend.telegram.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateAdminCommand {

    private final MongoTemplate mongoTemplate;
    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public void execute(String chatId, String messageText) {
        if (!isAuthorized(chatId)) {
            telegramClient.sendMessage(chatId, "У вас немає прав для використання цієї команди.");
            return;
        }

        String[] parts = messageText.trim().split("\\s+");
        if (parts.length < 2) {
            telegramClient.sendMessage(chatId, "Використання: /createadmin email@example.com [ім'я]");
            return;
        }

        String email = parts[1];
        String name = parts.length >= 3 ? messageText.substring(messageText.indexOf(parts[2])) : email.split("@")[0];

        if (adminExists(email)) {
            telegramClient.sendMessage(chatId, "Адміністратор з email <b>" + escapeHtml(email) + "</b> вже існує.");
            return;
        }

        String rawPassword = generatePassword();
        String hashedPassword = passwordEncoder.encode(rawPassword);

        Document adminUser = new Document()
                .append("_id", UUID.randomUUID().toString())
                .append("email", email)
                .append("password", hashedPassword)
                .append("name", name)
                .append("createdAt", Instant.now().toString());

        mongoTemplate.getCollection("admin_users").insertOne(adminUser);

        log.info("Admin user created via Telegram: {}", email);

        String message = """
                <b>Обліковий запис адміністратора створено</b>

                <b>Email:</b> <code>%s</code>
                <b>Пароль:</b> <code>%s</code>
                <b>Ім'я:</b> %s

                Використовуйте ці дані для входу:
                <code>POST /api/v1/auth/login</code>

                <b>Змініть пароль після першого входу!</b>
                """.formatted(escapeHtml(email), escapeHtml(rawPassword), escapeHtml(name));

        telegramClient.sendMessage(chatId, message);
    }

    private boolean isAuthorized(String chatId) {
        return telegramProperties.getChatIds() != null
                && telegramProperties.getChatIds().contains(chatId);
    }

    private boolean adminExists(String email) {
        Document found = mongoTemplate.getCollection("admin_users")
                .find(new Document("email", email))
                .first();
        return found != null;
    }

    private String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%";
        StringBuilder sb = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
