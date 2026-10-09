package com.alramz.jwt.aspect;

import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.jwt.context.JwtContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

@Aspect
public class JwtSecuredAspect {

    private static final Logger logger = LoggerFactory.getLogger(JwtSecuredAspect.class);

    private final JwtContext jwtContext;

    public JwtSecuredAspect(JwtContext jwtContext) {
        this.jwtContext = jwtContext;
    }

    @Before("@annotation(jwtSecured)")
    public void checkJwtSecured(JoinPoint joinPoint, JwtSecured jwtSecured) {
        if (!SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            if (logger.isWarnEnabled()) {
                logger.warn("Access denied to {}: unauthenticated", joinPoint.getSignature());
            }
            throw new AccessDeniedException("Unauthenticated");
        }

        checkRoles(joinPoint, jwtSecured.roles());
        checkEnvironment(joinPoint, jwtSecured.environment());

        if (logger.isDebugEnabled()) {
            logger.debug("Access granted to {} with roles={}, environment={}", joinPoint.getSignature(), Arrays.toString(jwtSecured.roles()), Arrays.toString(jwtSecured.environment()));
        }
    }

    private void checkRoles(JoinPoint joinPoint, String[] requiredRoles) {
        if (requiredRoles == null || requiredRoles.length == 0) {
            return;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean hasRole = Arrays.stream(requiredRoles)
                .anyMatch(role -> authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_" + role)));
        if (!hasRole) {
            if (logger.isWarnEnabled()) {
                logger.warn("Access denied to {}: insufficient roles. Required one of: {}", joinPoint.getSignature(), Arrays.toString(requiredRoles));
            }
            throw new AccessDeniedException("Insufficient role. Required one of: " + Arrays.toString(requiredRoles));
        }
    }

    private void checkEnvironment(JoinPoint joinPoint, String[] requiredEnvironments) {
        if (requiredEnvironments == null || requiredEnvironments.length == 0) {
            return;
        }

        String currentEnv = jwtContext.getCurrentEnvironment();
        if (currentEnv == null || currentEnv.isBlank()) {
            if (logger.isWarnEnabled()) {
                logger.warn("Access denied to {}: environment claim missing in token", joinPoint.getSignature());
            }
            throw new AccessDeniedException("Environment claim missing in token");
        }

        boolean hasEnv = Arrays.stream(requiredEnvironments)
                .anyMatch(env -> env.equals(currentEnv));
        if (!hasEnv) {
            if (logger.isWarnEnabled()) {
                logger.warn("Access denied to {}: insufficient environment. Required one of: {}, found: {}", joinPoint.getSignature(), Arrays.toString(requiredEnvironments), currentEnv);
            }
            throw new AccessDeniedException("Insufficient environment. Required one of: " + Arrays.toString(requiredEnvironments));
        }
    }
}
