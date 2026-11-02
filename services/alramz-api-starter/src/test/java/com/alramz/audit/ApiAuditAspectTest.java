package com.alramz.audit;

import com.alramz.client.AuditProbeClient;
import com.alramz.controllers.AuditProbeController;
import com.alramz.logging.util.MDCUtil;
import com.alramz.service.impl.IBANValidationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiAuditAspectTest {

    private final ApiAuditLogService auditLogService = mock(ApiAuditLogService.class);
    private final ApiAuditAspect aspect = new ApiAuditAspect(auditLogService,
            new SensitiveDataMasker(new ObjectMapper(), true),
            new MockEnvironment().withProperty("spring.application.name", "test-service"));

    @SuppressWarnings("unused")
    static class AuditedTarget {
        public String call(String endpoint) {
            return "ok";
        }

        public String send(String path, String method, String password, String body, String unused) {
            return "sent";
        }

        public String fetchAndCacheToken(String clientSecret) {
            return "token";
        }
    }

    private ApiAuditLog invoke(java.lang.reflect.Method method, Object[] args, Object result, Throwable failure)
            throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        if (failure != null) {
            when(joinPoint.proceed()).thenThrow(failure);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> aspect.auditApiCall(joinPoint)).isSameAs(failure);
        } else {
            when(joinPoint.proceed()).thenReturn(result);
            assertThat(aspect.auditApiCall(joinPoint)).isEqualTo(result);
        }
        return recordedEntry();
    }

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    private Object invokeAudited() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(AuditedTarget.class.getMethod("call", String.class));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[] {"/upstream"});
        when(joinPoint.proceed()).thenReturn("ok");
        return aspect.auditApiCall(joinPoint);
    }

    private ApiAuditLog recordedEntry() {
        ArgumentCaptor<ApiAuditLog> entry = ArgumentCaptor.forClass(ApiAuditLog.class);
        verify(auditLogService).log(entry.capture());
        return entry.getValue();
    }

    @Test
    void returnsTheRealResultWhenTheCorrelationIdIsNotAUuid() throws Throwable {
        MDCUtil.putCorrelationId("not-a-uuid");

        assertThat(invokeAudited()).isEqualTo("ok");
        assertThat(recordedEntry().correlationId()).isNotNull();
    }

    @Test
    void keepsAValidCorrelationId() throws Throwable {
        MDCUtil.putCorrelationId("2afe4a42-44f7-4e12-aae7-df0e42d4d757");

        invokeAudited();

        assertThat(recordedEntry().correlationId().toString()).isEqualTo("2afe4a42-44f7-4e12-aae7-df0e42d4d757");
    }

    // --- the real pointcut, applied through a Spring AOP proxy

    private <T> T proxied(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    void doesNotAuditControllersBecauseApiAuditLogFilterAlreadyRecordsEveryRequest() {
        assertThat(proxied(new AuditProbeController()).create("body")).isEqualTo("created");

        verify(auditLogService, never()).log(any());
    }

    @Test
    void stillAuditsOutboundClientCalls() {
        assertThat(proxied(new AuditProbeClient()).fetch("/upstream")).isEqualTo("fetched");

        ApiAuditLog entry = recordedEntry();
        assertThat(entry.direction()).isEqualTo("OUTBOUND");
        assertThat(entry.controllerName()).isEqualTo("AuditProbeClient.fetch");
    }

    @Test
    void stillAuditsTheOnboardingMethodsNamedInThePointcut() {
        assertThat(proxied(new IBANValidationServiceImpl()).validate("AE070331234567890123456")).isTrue();

        assertThat(recordedEntry().controllerName()).isEqualTo("IBANValidationServiceImpl.validate");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolvesPathAndHttpMethodFromParametersAndMasksSensitiveArgs() throws Throwable {
        ApiAuditLog entry = invoke(AuditedTarget.class.getMethod("send", String.class, String.class, String.class,
                String.class, String.class), new Object[] {"/v1/items", "PUT", "pw", "{}", null}, "sent", null);

        assertThat(entry.apiEndpoint()).isEqualTo("/v1/items");
        assertThat(entry.method()).isEqualTo("PUT");
        assertThat(entry.status()).isEqualTo("SUCCESS");
        assertThat(entry.serviceName()).isEqualTo("test-service");
        java.util.Map<String, Object> request = (java.util.Map<String, Object>) entry.request();
        assertThat(request).containsEntry("path", "/v1/items").containsEntry("password", "***")
                .doesNotContainKey("unused");
    }

    @Test
    void tokenFetchUsesKnownEndpointAndPost() throws Throwable {
        ApiAuditLog entry = invoke(AuditedTarget.class.getMethod("fetchAndCacheToken", String.class),
                new Object[] {"s3cr3t"}, "token", null);

        assertThat(entry.apiEndpoint()).isEqualTo("/IntegrationAPI/IntegrationWServices/GetClientToken");
        assertThat(entry.method()).isEqualTo("POST");
        assertThat(entry.request()).isEqualTo(java.util.Map.of("clientSecret", "***"));
    }

    @Test
    void fallsBackToMethodNameWhenEndpointBlank() throws Throwable {
        ApiAuditLog entry = invoke(AuditedTarget.class.getMethod("call", String.class),
                new Object[] {" "}, "ok", null);

        assertThat(entry.apiEndpoint()).isEqualTo("call");
        assertThat(entry.method()).isEqualTo("POST");
    }

    @Test
    void failureRecordsExceptionChainAndRethrows() throws Throwable {
        Exception root = new IllegalArgumentException("root cause");
        Exception failure = new IllegalStateException("upstream failed", new RuntimeException((String) null, root));

        ApiAuditLog entry = invoke(AuditedTarget.class.getMethod("call", String.class),
                new Object[] {"/upstream"}, null, failure);

        assertThat(entry.status()).isEqualTo("FAILURE");
        assertThat(entry.exceptionClass()).isEqualTo(IllegalStateException.class.getName());
        assertThat(entry.exceptionCause())
                .startsWith("java.lang.IllegalStateException: upstream failed\n  at ")
                .contains("\nCaused by: java.lang.RuntimeException\n")
                .contains("\nCaused by: java.lang.IllegalArgumentException: root cause")
                .contains(" more");
        assertThat(entry.response()).isNull();
    }
}
