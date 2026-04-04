package com.africe.backend.notification.telegram;

import com.africe.backend.auth.repository.AdminUserRepository;
import com.africe.backend.common.model.AdminUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class BotCommandPoller {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final List<String> adminChatIds;

    public BotCommandPoller(AdminUserRepository adminUserRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${telegram.chat-ids}") List<String> adminChatIds) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminChatIds = adminChatIds;
    }

    public String handleCreateAdmin(String chatId, String commandText) {
        if (!adminChatIds.contains(chatId)) {
            return "Unauthorized";
        }

        // Expected format: /create_admin email password name
        String[] parts = commandText.trim().split("\\s+", 4);
        if (parts.length < 4) {
            return "Usage: /create_admin email password name";
        }

        String email = parts[1];
        String password = parts[2];
        String name = parts[3];

        if (adminUserRepository.existsByEmail(email)) {
            return "Admin with this email already exists";
        }

        AdminUser admin = AdminUser.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .name(name)
                .build();
        adminUserRepository.save(admin);

        log.info("Admin user created via Telegram: {}", email);
        return "Admin created: " + email;
    }
}
