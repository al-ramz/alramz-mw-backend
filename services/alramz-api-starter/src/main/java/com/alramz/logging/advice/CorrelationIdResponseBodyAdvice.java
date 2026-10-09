package com.alramz.logging.advice;

import com.alramz.logging.util.MDCUtil;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;
import java.util.UUID;

@RestControllerAdvice
public class CorrelationIdResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return null;
        }
        try {
            Method setter = findCorrelationIdSetter(body.getClass());
            if (setter != null) {
                String correlationId = MDCUtil.getCorrelationId();
                if (correlationId == null || correlationId.isBlank()) {
                    correlationId = UUID.randomUUID().toString();
                }
                Class<?> paramType = setter.getParameterTypes()[0];
                Object value = paramType.equals(UUID.class)
                        ? UUID.fromString(correlationId)
                        : correlationId;
                setter.invoke(body, value);
            }
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            // never break the response because of correlation id injection
        }
        return body;
    }

    private Method findCorrelationIdSetter(Class<?> clazz) {
        try {
            return clazz.getMethod("setCorrelationId", String.class);
        } catch (NoSuchMethodException e) {
            try {
                return clazz.getMethod("setCorrelationId", UUID.class);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }
}
