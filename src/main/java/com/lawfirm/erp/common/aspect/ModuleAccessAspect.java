package com.lawfirm.erp.common.aspect;

import com.lawfirm.erp.auth.security.AuthenticatedUser;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.annotation.RequiresModule;
import com.lawfirm.erp.common.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ModuleAccessAspect {

    private final PermissionEvaluator permissionEvaluator;
    private final CurrentUserResolver currentUserResolver;

    @Around("@annotation(requiresModule) || @within(requiresModule)")
    public Object enforce(ProceedingJoinPoint joinPoint, RequiresModule requiresModule) throws Throwable {
        String moduleCode = resolveModuleCode(joinPoint);

        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) {
            // Fail closed for firm-bound users: with no firm context the module gate cannot be
            // evaluated, which is a denial — not a pass. Only platform (super-admin) requests,
            // which genuinely have no firm, are allowed through.
            AuthenticatedUser user = currentUserResolver.getCurrentUser();
            if (user != null && user.getFirmId() != null) {
                throw new ForbiddenException(
                        "Module access could not be verified for your account. Please try again.");
            }
            return joinPoint.proceed();
        }

        // Single source of truth for module enablement — see PermissionEvaluator.hasModuleAccess.
        if (!permissionEvaluator.hasModuleAccess(firmId, moduleCode)) {
            log.info("Module '{}' is not enabled for firm {} — rejecting request", moduleCode, firmId);
            throw new ForbiddenException(
                    "Module '" + moduleCode + "' is not enabled for your firm. Please upgrade your plan.");
        }

        return joinPoint.proceed();
    }

    private String resolveModuleCode(ProceedingJoinPoint joinPoint) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();

        RequiresModule methodAnnotation = AnnotationUtils.findAnnotation(method, RequiresModule.class);
        if (methodAnnotation != null) {
            return methodAnnotation.value();
        }

        RequiresModule classAnnotation = AnnotationUtils.findAnnotation(
                joinPoint.getTarget().getClass(), RequiresModule.class);
        if (classAnnotation != null) {
            return classAnnotation.value();
        }

        throw new IllegalStateException("No @RequiresModule annotation found on " + method.getName());
    }
}
