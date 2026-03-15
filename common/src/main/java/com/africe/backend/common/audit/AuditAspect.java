package com.africe.backend.common.audit;

import com.africe.backend.common.model.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    @AfterReturning("@annotation(adminAudited)")
    public void audit(JoinPoint joinPoint, AdminAudited adminAudited) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String adminId = "unknown";
        String adminEmail = "unknown";

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            try {
                Method getIdMethod = principal.getClass().getMethod("getId");
                Method getEmailMethod = principal.getClass().getMethod("getEmail");
                adminId = (String) getIdMethod.invoke(principal);
                adminEmail = (String) getEmailMethod.invoke(principal);
            } catch (Exception e) {
                adminId = authentication.getName();
                adminEmail = authentication.getName();
            }
        }

        String action = adminAudited.action();

        saveAuditLog(adminId, adminEmail, action);
    }

    @Async
    protected void saveAuditLog(String adminId, String adminEmail, String action) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .adminId(adminId)
                    .adminEmail(adminEmail)
                    .action(action)
                    .createdAt(Instant.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log for action: {}", action, e);
        }
    }
}
