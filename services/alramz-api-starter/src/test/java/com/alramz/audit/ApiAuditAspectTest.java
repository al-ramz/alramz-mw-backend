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
}
