package com.alramz.audit;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.MDCUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

@Aspect
@Slf4j
public class ApiAuditAspect {

    private final ApiAuditLogService auditLogService;
    private final SensitiveDataMasker masker;
    private final Environment environment;

    public ApiAuditAspect(ApiAuditLogService auditLogService,
                          SensitiveDataMasker masker,
                          Environment environment) {
        this.auditLogService = auditLogService;
        this.masker = masker;
        this.environment = environment;
    }

    // Outbound calls only: ApiAuditLogFilter already writes the INBOUND row for every request, so
    // matching controllers here would audit each request twice.
    @Around("execution(* com.alramz.client..*(..)) || execution(* com.alramz.config.ETradeTokenProvider.fetchAndCacheToken(..)) || execution(* com.alramz.service.impl.IBANValidationServiceImpl.validate(..)) || execution(* com.alramz.service.impl.PhoneValidationServiceImpl.validate(..)) || execution(* com.alramz.service.impl.DuplicateCheckServiceImpl..*(..))")
    public Object auditApiCall(ProceedingJoinPoint pjp) throws Throwable {
        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";
        String exceptionCause = null;
        String exceptionClass = null;
        Object response = null;

        try {
            response = pjp.proceed();
            return response;
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            status = "FAILURE";
            exceptionClass = e.getClass().getName();
            exceptionCause = buildDetailedExceptionCause(e);
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;

            MethodSignature signature = (MethodSignature) pjp.getSignature(); // NOPMD LawOfDemeter
            Method method = signature.getMethod();
            Class<?> declaringClass = method.getDeclaringClass(); // NOPMD LawOfDemeter

            String className = declaringClass.getSimpleName();
            String methodName = method.getName(); // NOPMD LawOfDemeter
            String controllerName = className + "." + methodName;

            String endpoint = resolveEndpoint(method, pjp.getArgs());
            String httpMethod = resolveHttpMethod(method, pjp.getArgs());


            UUID correlationId = MDCUtil.getCorrelationIdAsUuid();

            String serviceName = environment.getProperty("spring.application.name", "unknown");

            Object requestPayload = masker.mask(buildRequestMap(method, pjp.getArgs()));

            auditLogService.log(new ApiAuditLog(
                    correlationId,
                    "OUTBOUND",
                    serviceName,
                    controllerName,
                    endpoint,
                    httpMethod,
                    requestPayload,
                    masker.mask(response),
                    status,
                    null,
                    exceptionCause,
                    exceptionClass,
                    durationMs,
                    java.time.Instant.now()
            ));
        }
    }

    private String resolveEndpoint(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName().toLowerCase();
            if ((name.contains("endpoint") || name.contains("path")) && args[i] instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        if (method.getName().equals("fetchAndCacheToken")) {
            return "/IntegrationAPI/IntegrationWServices/GetClientToken";
        }

        return method.getName();
    }

    private String resolveHttpMethod(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName().toLowerCase();
            if ((name.equals("method") || name.equals("httpMethod")) && args[i] instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        if (method.getName().equals("fetchAndCacheToken")) {
            return "POST";
        }

        return "POST";
    }

    private Map<String, Object> buildRequestMap(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < parameters.length; i++) {
            Object value = args[i];
            if (value == null) {
                continue;
            }
            String paramName = parameters[i].getName();
            if (isSensitiveParameter(paramName)) {
                map.put(paramName, "***");
            } else {
                map.put(paramName, value);
            }
        }
        return map;
    }

    private boolean isSensitiveParameter(String paramName) {
        if (paramName == null) {
            return false;
        }
        String lower = paramName.toLowerCase();
        return Set.of(
                "accesstoken", "access_token", "client_secret", "clientsecret",
                "password", "secret", "authorization", "api_key", "apikey"
        ).contains(lower);
    }

    private String buildDetailedExceptionCause(Exception e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        int depth = 0;
        int maxDepth = 3;
        int maxFrames = 5;

        while (current != null && depth < maxDepth) {
            if (depth > 0) {
                sb.append("\nCaused by: ");
            }
            sb.append(current.getClass().getName());
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                sb.append(": ").append(current.getMessage());
            }

            StackTraceElement[] frames = current.getStackTrace();
            if (frames != null && frames.length > 0) {
                sb.append("\n  at ");
                int limit = Math.min(maxFrames, frames.length);
                for (int i = 0; i < limit; i++) {
                    if (i > 0) sb.append("\n  at ");
                    sb.append(frames[i]);
                }
                if (frames.length > maxFrames) {
                    sb.append("\n  ... ").append(frames.length - maxFrames).append(" more");
                }
            }

            current = current.getCause();
            depth++;
        }

        return sb.toString();
    }
}
