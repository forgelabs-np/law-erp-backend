package com.lawfirm.erp.common.aspect;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.annotation.RequiresModule;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.firm.repository.FirmModuleRepository;
import com.lawfirm.erp.firm.service.ModuleAccessResolver;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces {@link RequiresModule} at the AOP level — any controller class or method
 * carrying this annotation is checked against the firm's enabled modules before the
 * request enters the service layer.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ModuleAccessAspect {

    private final FirmModuleRepository firmModuleRepository;
    private final ModuleRepository moduleRepository;

    @Around("@annotation(requiresModule) || @within(requiresModule)")
    public Object enforce(ProceedingJoinPoint joinPoint, RequiresModule requiresModule) throws Throwable {
        // Resolve the module code — method-level annotation overrides class-level
        String moduleCode = resolveModuleCode(joinPoint);

        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) {
            // Super Admin / unscoped — skip module check
            return joinPoint.proceed();
        }

        Module module = moduleRepository.findByCode(moduleCode).orElse(null);
        if (module == null) {
            log.warn("Module '{}' not found in registry — treating as disabled", moduleCode);
            throw new ForbiddenException(
                    "Module '" + moduleCode + "' is not available. Please contact your administrator.");
        }

        Map<UUID, FirmModule> indexed = ModuleAccessResolver.indexByModuleId(
                firmModuleRepository.findByFirmId(firmId));

        if (!ModuleAccessResolver.isEnabled(module, indexed)) {
            log.info("Module '{}' is not enabled for firm {} — rejecting request", moduleCode, firmId);
            throw new ForbiddenException(
                    "Module '" + moduleCode + "' is not enabled for your firm. Please upgrade your plan.");
        }

        return joinPoint.proceed();
    }

    private String resolveModuleCode(ProceedingJoinPoint joinPoint) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();

        // Prefer method-level annotation
        RequiresModule methodAnnotation = AnnotationUtils.findAnnotation(method, RequiresModule.class);
        if (methodAnnotation != null) {
            return methodAnnotation.value();
        }

        // Fall back to class-level annotation
        RequiresModule classAnnotation = AnnotationUtils.findAnnotation(
                joinPoint.getTarget().getClass(), RequiresModule.class);
        if (classAnnotation != null) {
            return classAnnotation.value();
        }

        // Should never reach here — the pointcut guarantees the annotation exists
        throw new IllegalStateException("No @RequiresModule annotation found on " + method.getName());
    }
}
