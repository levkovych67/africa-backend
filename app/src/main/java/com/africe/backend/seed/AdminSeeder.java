package com.africe.backend.seed;

import com.africe.backend.auth.repository.AdminUserRepository;
import com.africe.backend.common.model.AdminUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true")
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${seed.admin.email:admin@africe.com}")
    private String adminEmail;

    @Value("${seed.admin.password:admin123}")
    private String adminPassword;

    public AdminSeeder(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (adminUserRepository.count() > 0) {
            log.info("Admin users already exist, skipping seed");
            return;
        }

        AdminUser admin = AdminUser.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .name("Admin")
                .build();

        adminUserRepository.save(admin);
        log.info("Default admin user created with email: {} — change password after first login", adminEmail);
    }
}
