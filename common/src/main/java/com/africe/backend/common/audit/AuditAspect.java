package com.africe.backend.common.audit;

import com.africe.backend.common.model.AuditLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private final MongoTemplate mongoTemplate;

    public AuditAspect(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Around("@annotation(adminAudited)")
    public Object audit(ProceedingJoinPoint joinPoint, AdminAudited adminAudited) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminId = auth != null ? auth.getName() : "unknown";
            String adminEmail = auth != null && auth.getDetails() instanceof String details ? details : "unknown";

            String action = adminAudited.action().isEmpty()
                    ? joinPoint.getSignature().getName()
                    : adminAudited.action();

            String targetType = adminAudited.targetType().isEmpty()
                    ? resolveTargetType(joinPoint)
                    : adminAudited.targetType();

            String targetId = resolveTargetId(joinPoint);

            AuditLog auditLog = AuditLog.builder()
                    .adminId(adminId)
                    .adminEmail(adminEmail)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .timestamp(Instant.now())
                    .build();

            mongoTemplate.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }

        return result;
    }

    private String resolveTargetType(ProceedingJoinPoint joinPoint) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String className = sig.getDeclaringType().getSimpleName();
        return className.replace("Controller", "").replace("Admin", "");
    }

    private String resolveTargetId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof String id) {
            return id;
        }
        return null;
    }
}
