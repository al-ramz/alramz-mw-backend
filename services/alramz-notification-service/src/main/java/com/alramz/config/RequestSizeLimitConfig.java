package com.alramz.config;

import com.alramz.exception.EmailValidationException;
import com.alramz.exception.TemplatedEmailException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.function.Supplier;

/**
 * Rejects an oversized send request (413 MGE-V-008, or EML007 on the template path) before its JSON body is read. Runs after Spring Security,
 * so unauthenticated callers still get 401. The limit is derived from the attachment cap: Base64 adds a third,
 * plus 256 KiB for the rest of the message.
 */
@Configuration
@RequiredArgsConstructor
public class RequestSizeLimitConfig implements WebMvcConfigurer {

    private static final long ENVELOPE_ALLOWANCE_BYTES = 262_144;

    private final EmailProperties emailProperties;

    private static final String SIZE_EXCEEDED = "Attachments size limit exceeded";

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(sizeLimitInterceptor()).addPathPatterns("/api/v1/email/send");
        registry.addInterceptor(templateSizeLimitInterceptor()).addPathPatterns("/api/v1/email/template/send");
    }

    HandlerInterceptor sizeLimitInterceptor() {
        return sizeLimitInterceptor(() -> new EmailValidationException("MGE-V-008", SIZE_EXCEEDED));
    }

    HandlerInterceptor templateSizeLimitInterceptor() {
        return sizeLimitInterceptor(() -> new TemplatedEmailException("EML007", SIZE_EXCEEDED));
    }

    private HandlerInterceptor sizeLimitInterceptor(final Supplier<RuntimeException> tooLarge) {
        final long maxRequestBytes = emailProperties.maxTotalAttachmentSizeBytes() * 4 / 3 + ENVELOPE_ALLOWANCE_BYTES;
        // ponytail: chunked requests (no Content-Length) bypass this; the decoded-total check after binding still applies. Bound the input stream if chunked abuse appears.
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response,
                                     final Object handler) {
                if (request.getContentLengthLong() > maxRequestBytes) {
                    throw tooLarge.get();
                }
                return true;
            }
        };
    }
}
