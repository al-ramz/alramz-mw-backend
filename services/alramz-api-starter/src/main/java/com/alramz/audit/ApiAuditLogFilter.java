package com.alramz.audit;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.MDCUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.UUID;

@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ApiAuditLogFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ApiAuditLogFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final LoggingProperties properties;
    private final ApiAuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final SensitiveDataMasker masker;
    private final Environment environment;

    public ApiAuditLogFilter(LoggingProperties properties,
                             Environment environment,
                             ApiAuditLogService auditLogService,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.environment = environment;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.masker = auditLogService != null ? auditLogService.getMasker() : new SensitiveDataMasker(objectMapper, properties.getMasking().isEnabled()); // NOPMD LawOfDemeter
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (auditLogService == null || !properties.getDatabaseLogging().isEnabled()) { // NOPMD LawOfDemeter
            return true;
        }
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        for (String pattern : properties.getDatabaseLogging().getExcludedPaths()) { // NOPMD LawOfDemeter
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 8192);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";
        String exceptionCause = null;
        String exceptionClass = null;

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            status = "FAILURE";
            exceptionClass = e.getClass().getName();
            exceptionCause = e.getMessage();
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;

            if (response.getStatus() >= 400) {
                status = "FAILURE";
            }

            if (status.equals("FAILURE") && exceptionCause == null) {
                try {
                    String responseBody = new String(wrappedResponse.getContentAsByteArray(), response.getCharacterEncoding());
                    exceptionCause = extractErrorFromResponse(responseBody);
                    exceptionClass = "HttpStatusCodeException";
                } catch (Exception e) { // NOPMD AvoidCatchingGenericException
                    // ignore parsing errors
                }
            }

            UUID correlationId = MDCUtil.getCorrelationIdAsUuid();

            Object handler = request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
            String controllerName;
            if (handler instanceof HandlerMethod handlerMethod) {
                // What Spring MVC stores for every @RestController endpoint.
                controllerName = handlerMethod.getBeanType().getSimpleName() + "." + handlerMethod.getMethod().getName();
            } else if (handler instanceof Class<?> clazz) {
                controllerName = clazz.getSimpleName();
            } else if (handler instanceof String beanName) {
                controllerName = beanName;
            } else {
                controllerName = "unknown";
            }

            String endpoint = request.getRequestURI();
            String method = request.getMethod();
            String serviceName = environment.getProperty("spring.application.name", "unknown");

            Object requestPayload = null;
            Object responsePayload = null;

            if (properties.getDatabaseLogging().isIncludeRequestPayload()) { // NOPMD LawOfDemeter
                requestPayload = masker.maskRequestBody(wrappedRequest);
            }
            if (properties.getDatabaseLogging().isIncludeResponsePayload()) { // NOPMD LawOfDemeter
                responsePayload = masker.maskResponseBody(wrappedResponse);
            }

            ApiAuditLog auditLog = new ApiAuditLog(
                    correlationId,
                    "INBOUND",
                    serviceName,
                    controllerName,
                    endpoint,
                    method,
                    requestPayload,
                    responsePayload,
                    status,
                    response.getStatus(),
                    exceptionCause,
                    exceptionClass,
                    durationMs,
                    java.time.Instant.now()
            );

            if (auditLogService != null) {
                auditLogService.log(auditLog);
            }

            wrappedResponse.copyBodyToResponse();
        }
    }

    private String extractErrorFromResponse(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            JsonNode errors = node.path("response").path("errors");
            if (errors.isArray() && errors.size() > 0) {
                JsonNode firstError = errors.get(0);
                String code = firstError.path("code").asText("");
                String message = firstError.path("message").asText("");
                return "[" + code + "] " + message;
            }
            return node.path("responseMessage").asText("Unknown error");
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            return responseBody != null && responseBody.length() > 500
                    ? responseBody.substring(0, 500) : responseBody;
        }
    }
}