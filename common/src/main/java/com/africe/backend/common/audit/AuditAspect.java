package com.africe.backend.common.audit;

import com.africe.backend.common.model.AuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Instant;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Around("@annotation(adminAudited)")
    public Object audit(ProceedingJoinPoint joinPoint, AdminAudited adminAudited) throws Throwable {
        // Extract targetId from @PathVariable params before execution
        String targetId = extractPathVariable(joinPoint);

        // Execute the method
        Object result = joinPoint.proceed();

        // If no targetId from path (CREATE), try to get from result
        if (targetId == null && result != null) {
            targetId = extractIdFromResult(result);
        }

        // Extract @RequestBody for details
        String details = extractRequestBody(joinPoint);

        // Get admin info from security context
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

        saveAuditLog(adminId, adminEmail, adminAudited.action(), adminAudited.targetType(), targetId, details);

        return result;
    }

    private String extractPathVariable(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] paramAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof PathVariable && args[i] instanceof String) {
                    return (String) args[i];
                }
            }
        }
        return null;
    }

    private String extractIdFromResult(Object result) {
        try {
            Method getIdMethod = result.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(result);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractRequestBody(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] paramAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof RequestBody && args[i] != null) {
                    try {
                        return objectMapper.writeValueAsString(args[i]);
                    } catch (Exception e) {
                        return args[i].toString();
                    }
                }
            }
        }
        return null;
    }

    @Async
    protected void saveAuditLog(String adminId, String adminEmail, String action, String targetType, String targetId, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .adminId(adminId)
                    .adminEmail(adminEmail)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .details(details)
                    .createdAt(Instant.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log for action: {}", action, e);
        }
    }
}
