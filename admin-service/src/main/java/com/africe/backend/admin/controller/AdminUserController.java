package com.africe.backend.admin.controller;

import com.africe.backend.auth.repository.AdminUserRepository;
import com.africe.backend.common.audit.AdminAudited;
import com.africe.backend.common.dto.AdminUserResponse;
import com.africe.backend.common.dto.CreateAdminRequest;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.AdminUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(AdminUserRepository adminUserRepository,
                               PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<AdminUserResponse> listAdmins() {
        return adminUserRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @AdminAudited(action = "CREATE_ADMIN")
    public AdminUserResponse createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        if (adminUserRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Адмін з таким email вже існує");
        }

        AdminUser admin = AdminUser.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .build();

        return toResponse(adminUserRepository.save(admin));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(action = "DELETE_ADMIN")
    public void deleteAdmin(@PathVariable String id) {
        if (!adminUserRepository.existsById(id)) {
            throw new ResourceNotFoundException("AdminUser", "id", id);
        }
        if (adminUserRepository.count() <= 1) {
            throw new IllegalArgumentException("Неможливо видалити останнього адміна");
        }
        adminUserRepository.deleteById(id);
    }

    private AdminUserResponse toResponse(AdminUser admin) {
        return new AdminUserResponse(
                admin.getId(),
                admin.getEmail(),
                admin.getName(),
                admin.getCreatedAt()
        );
    }
}
